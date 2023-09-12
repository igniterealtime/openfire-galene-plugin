package org.ifsoft.galene.openfire;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletOutputStream;

import java.io.IOException;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.muc.*;

import org.xmpp.packet.*;
import net.sf.json.*;

public class AuthServer extends HttpServlet {
    private static final Logger Log = LoggerFactory.getLogger(AuthServer.class);
	
	private String normaliseLocation(String location) {
		int pos1 = location.indexOf("/group/");			
		if (pos1 > -1) return location;

		int pos2 = location.indexOf("?");
		
		if (pos2 > -1) {
			String params[] = location.substring(pos2 + 1).split("&");
			String prefix = location.substring(0, pos2);
			if (!prefix.endsWith("/")) prefix += "/";
			
			for (int i=0; i<params.length; i++) {
				String pairs[] = params[i].split("=");
				
				if ("room".equals(pairs[0])) {
					return "http://localhost:" + Galene.self.getPort() + "/group/" + pairs[1] + "/";
				}
			}
		}
		return location;
	}
	
	private void sendAcceptedResponse(HttpServletResponse response, JSONArray permissions, String username, String location) {
		JSONObject jwtPayload = new JSONObject();		
		LocalDateTime iat = LocalDateTime.now().minusDays(1);
		LocalDateTime ldt = iat.plusDays(2);	

		jwtPayload.put("sub", username);
		jwtPayload.put("aud", location);
		jwtPayload.put("permissions", permissions);			
		jwtPayload.put("iat", iat.toEpochSecond(ZoneOffset.UTC));
		jwtPayload.put("exp", ldt.toEpochSecond(ZoneOffset.UTC));
		jwtPayload.put("iss", "https://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + JiveGlobals.getProperty("httpbind.port.secure", "7443") + "/galene/auth-server");			
				
		String token = new JWebToken(jwtPayload).toString();			
		Log.info("AuthServer token\n" + token);
		response.setHeader("content-type", "application/jwt");		

		try {
			response.getOutputStream().print(token);
			response.setStatus(HttpServletResponse.SC_ACCEPTED);

		} catch (Exception ex) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);				
		}			
	}
	

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String body = request.getReader().lines().collect(Collectors.joining());
        Log.info("AuthServer post\n" + body);
		
		try {			
            String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();	
			
			JSONObject json = new JSONObject(body);
			String username = json.getString("username");
			String password = json.getString("password");
			String location = normaliseLocation(json.getString("location"));
				
			JID jid = null;
			ClientSession session = null;
			MUCRoom mucRoom = null;			
			
			if (!"".equals(username) && !"".equals(password) && !"undefined".equals(username) && !"undefined".equals(password) && !"null".equals(username) && !"null".equals(password)) {				
				try {
					jid = new JID(password);	// password is full JID of user to identify user. User is already authenticated via XMPP
					
					if (domain.equals(jid.getDomain())) {				
						session = SessionManager.getInstance().getSession(jid);
						
						if (session == null) {	
							Log.warn("Can't find a session for " + jid);						
							response.setStatus(HttpServletResponse.SC_FORBIDDEN);
							return;
						}
						else
							
						if (!username.equals(jid.getNode()) && !session.isAnonymousUser()) {
							Log.warn("Invalid session for " + jid + " " + username);						
							response.setStatus(HttpServletResponse.SC_FORBIDDEN);
							return;							
						}

						String adminUsername = JiveGlobals.getProperty("galene.username", "sfu-admin");
						
						if (username.equals(adminUsername)) {	// superuser
							JSONArray permissions = new JSONArray();
							permissions.put(0, "record");	
							permissions.put(1, "op");							
							permissions.put(2, "present");	
							permissions.put(3, "token");					
						
							sendAcceptedResponse(response, permissions, username, location);
							Log.warn("Identified sfu user " + jid);							
							return;
						}
				
					} else 	{
						Log.warn("bad user identification " + jid);						
						response.setStatus(HttpServletResponse.SC_FORBIDDEN);
						return;
					}						

				} catch (Exception ex) {
					Log.warn("bad user identification " + password);						
					response.setStatus(HttpServletResponse.SC_FORBIDDEN);
					return;					
				}

				int perm = 0;					
				String room = location.split("/")[4];			
								
				if (room != null) {					
					Log.info("AuthServer location " + room + " " + location);
										
					if ("public".equals(room)) {
						Log.info("found public room " + room);							
						response.setStatus(HttpServletResponse.SC_NO_CONTENT);	
						return;						
					} else {
						mucRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(room);									
					}
					
					if (mucRoom == null) {
						Log.warn("no room found " + room);							
						response.setStatus(HttpServletResponse.SC_NO_CONTENT);
						return;
					}					
						
					if (session != null && session.isAnonymousUser()) {
						Log.warn("Anonymous User " + jid);	
						
						if (mucRoom.isMembersOnly()) {
							response.setStatus(HttpServletResponse.SC_FORBIDDEN);
						} else {
							response.setStatus(HttpServletResponse.SC_NO_CONTENT);
						}
						return;						
					}	

					boolean isOccupant = false;
					
					for (MUCRole role : mucRoom.getOccupants()) {
						Log.info("matching room occupant " + role.getUserAddress() + " with " + jid );
						
						if (role.getUserAddress().getNode().equals(jid.getNode())) {
							isOccupant = true;
							
							if (MUCRole.Affiliation.member == role.getAffiliation()) perm = 1;
							if (MUCRole.Affiliation.admin == role.getAffiliation()) perm = 2;
							if (MUCRole.Affiliation.owner == role.getAffiliation()) perm = 3;	
							break;
						}
					}
					
					if (!isOccupant) {
						Log.warn("Can't find a room occupant for " + jid);
						response.setStatus(HttpServletResponse.SC_NO_CONTENT);
						return;
					}					

					Log.warn("found room occupant with permissions " + perm);
					JSONArray permissions = new JSONArray();
					
					if (perm == 3) {
						permissions.put(0, "record");	
						permissions.put(1, "op");							
						permissions.put(2, "present");	
						permissions.put(3, "token");					
					} 
					else

					if (perm == 2) {
						permissions.put(0, "op");							
						permissions.put(1, "present");	
						permissions.put(2, "token");						
					}	
					else

					if (perm == 1) {						
						permissions.put(0, "present");	
						permissions.put(1, "token");						
					}	
					else {
						if (mucRoom.canOccupantsInvite()) permissions.put(0, "token");
					}
					
					sendAcceptedResponse(response, permissions, username, location);
					
				} else {
					Log.warn("no room found, bad location " + location);						
					response.setStatus(HttpServletResponse.SC_FORBIDDEN);
					return;
				}					
					
			} else {
				Log.warn("bad username " + username);				
				response.setStatus(HttpServletResponse.SC_NO_CONTENT);
				return;
			}			
			
		} catch (Exception e) {
			Log.error("AuthServer post " + e, e);
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);	
			return;
		}
	}
	
}


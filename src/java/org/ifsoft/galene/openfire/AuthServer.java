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

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String body = request.getReader().lines().collect(Collectors.joining());
        Log.info("AuthServer post\n" + body);
		
		try {
            String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();			
			JSONObject json = new JSONObject(body);
			String username = json.getString("username");
			String password = json.getString("password");
			JID jid = null;
			ClientSession session = null;
			MUCRoom mucRoom = null;			
			
			if (!"".equals(username) && !"".equals(password) && !"undefined".equals(username) && !"undefined".equals(password) && !"null".equals(username) && !"null".equals(password)) {
				try {
					jid = new JID(password);
					
					if (domain.equals(jid.getDomain())) {				
						session = SessionManager.getInstance().getSession(jid);
						
						if (session == null) {		
							response.setStatus(HttpServletResponse.SC_FORBIDDEN);
							return;
						}						
					}						

				} catch (Exception ex) {
					response.setStatus(HttpServletResponse.SC_FORBIDDEN);
					return;					
				}

				int perm = 0;				
				String location = normaliseLocation(json.getString("location"));	
				String room = location.split("/")[4];			
								
				if (room != null) {					
					Log.info("AuthServer location " + room + " " + location);
										
					if ("public".equals(room)) {
						response.setStatus(HttpServletResponse.SC_NO_CONTENT);	
						return;						
					} else {
						mucRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(room);									
					}
					
					if (mucRoom == null) {
						response.setStatus(HttpServletResponse.SC_NO_CONTENT);
						return;
					}					
						
					if (session != null && session.isAnonymousUser())
					{
						if (mucRoom.isMembersOnly()) {
							response.setStatus(HttpServletResponse.SC_FORBIDDEN);
						} else {
							response.setStatus(HttpServletResponse.SC_NO_CONTENT);
						}
						return;						
					}	

					boolean isOccupant = false;
					
					for (MUCRole role : mucRoom.getOccupants()) 
					{
						if (role.getUserAddress().toString().equals(jid.toString())) {
							isOccupant = true;
							
							if (MUCRole.Affiliation.member == role.getAffiliation()) perm = 1;
							if (MUCRole.Affiliation.admin == role.getAffiliation()) perm = 2;
							if (MUCRole.Affiliation.owner == role.getAffiliation()) perm = 3;	
							break;
						}
					}
					
					if (!isOccupant) {
						response.setStatus(HttpServletResponse.SC_NO_CONTENT);
						return;
					}					

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
					response.getOutputStream().print(token);
					response.setStatus(HttpServletResponse.SC_ACCEPTED);
					
				} else {
					response.setStatus(HttpServletResponse.SC_FORBIDDEN);
					return;
				}					
					
			} else {
				if (mucRoom != null && mucRoom.isMembersOnly()) {
					response.setStatus(HttpServletResponse.SC_FORBIDDEN);
				} else {
					response.setStatus(HttpServletResponse.SC_NO_CONTENT);
				}
				return;
			}			
			
		} catch (Exception e) {
			Log.error("AuthServer post", e);
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);	
			return;
		}
	}
	
}

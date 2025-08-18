package org.ifsoft.galene.openfire;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletOutputStream;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.jivesoftware.openfire.sasl.AnonymousSaslServer;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.openfire.group.*;

import org.xmpp.packet.*;
import net.sf.json.*;

public class AuthServer extends HttpServlet {
    private static final Logger Log = LoggerFactory.getLogger(AuthServer.class);
	
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
		Log.debug("AuthServer token\n" + token);
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
		
		try {			
            String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();				
			JSONObject json = new JSONObject(body);
			String username = json.getString("username");
			String password = json.getString("password");
			String location = json.getString("location");
			MUCRoom mucRoom = null;				
			String room = location.split("/")[4];			
							
			if (room != null) {					
				Log.debug("AuthServer location " + room + " " + location);
									
				if ("public".equals(room)) {
					Log.debug("found public room " + room);

					if (AnonymousSaslServer.ENABLED.getValue()) {
						response.setStatus(HttpServletResponse.SC_NO_CONTENT);	
					} else {
						response.setStatus(HttpServletResponse.SC_FORBIDDEN);
					}
					return;	
				} else {
					mucRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(room);									

					if (mucRoom == null) {
						Log.warn("no room found " + room);							
						response.setStatus(HttpServletResponse.SC_NO_CONTENT);
						return;
					}	
				}
	
			} else {
				Log.warn("no room found, bad location " + location);						
				response.setStatus(HttpServletResponse.SC_FORBIDDEN);
				return;
			}			

			Log.debug("AuthServer post " + username + " " + location + " " + room);			
			
			if (!"".equals(username) && !"".equals(password) && !"undefined".equals(username) && !"undefined".equals(password) && !"null".equals(username) && !"null".equals(password)) {				
				String adminUsername = JiveGlobals.getProperty("galene.username", "sfu-admin");
				String adminPassword = JiveGlobals.getProperty("galene.password", "sfu-admin");
				
				if (username.equals(adminUsername) && adminPassword.equals(password)) {	// superuser
					JSONArray permissions = new JSONArray();
					permissions.put(0, "record");	
					permissions.put(1, "op");							
					permissions.put(2, "present");	
					permissions.put(3, "token");					
				
					sendAcceptedResponse(response, permissions, username, location);
					Log.warn("Identified sfu user " + username);							
					return;
				}	

				JID jid = null;
				
				if (username.indexOf("@") > -1) {
					jid = new JID(username);
				} else {
					jid = new JID(username + "@" + domain);					
				}
			
				int perm = 0;									
				boolean isOwner = false;
				boolean isAdmin = false;
				boolean isMember = false;
				
				ArrayList<JID> owners = new ArrayList<>(mucRoom.getOwners());
				Collections.sort(owners);
				
				for (JID user : owners) {
					boolean isGroup = GroupJID.isGroup(user);					
					
					if (isGroup) {
						Group group = GroupManager.getInstance().getGroup(user);
						if (group.isUser(jid)) perm = 3;							
					} else {
						if (jid.toString().equals(user.toString())) perm = 3;
					}
				}
				
				ArrayList<JID> admins = new ArrayList<>(mucRoom.getAdmins());
				Collections.sort(admins);
				
				for (JID user : admins) {
					boolean isGroup = GroupJID.isGroup(user);					
					
					if (isGroup) {
						Group group = GroupManager.getInstance().getGroup(user);
						if (group.isUser(jid)) perm = 2;
					} else {
						if (jid.toString().equals(user.toString())) perm = 2;
					}
				}					

				ArrayList<JID> members = new ArrayList<>(mucRoom.getMembers());
				Collections.sort(members);
				
				for (JID user : members) {
					boolean isGroup = GroupJID.isGroup(user);					
					
					if (isGroup) {
						Group group = GroupManager.getInstance().getGroup(user);
						if (group.isUser(jid)) perm = 1;							
					} else {
						if (jid.toString().equals(user.toString())) perm = 1;
					}
				}										

				Log.warn("found room permissions " + perm);
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
					if (mucRoom.canOccupantsInvite()) {
						permissions.put(0, "token");
					}
				}
				
				String pass = mucRoom.getPassword();
				
				if (pass != null && !pass.isEmpty() && pass.equals(password))  { // room password			
					sendAcceptedResponse(response, permissions, username, location);
					Log.warn("authenticated occupant with room password " + jid);							
					return;					
				
				} else {				

					try {
						AuthFactory.authenticate(username, password);
						sendAcceptedResponse(response, permissions, username, location);
						return;							

					} catch (Exception ex) {
						Log.warn("bad user identification " + username);						
						response.setStatus(HttpServletResponse.SC_FORBIDDEN);
						return;					
					}					
				}					
					
			} else {
				
				if (AnonymousSaslServer.ENABLED.getValue() && !mucRoom.isMembersOnly()) {
					Log.warn("bad username " + username);				
					response.setStatus(HttpServletResponse.SC_NO_CONTENT);
					return;
				}
			}			
			
		} catch (Exception e) {
			Log.error("AuthServer post " + e, e);
		}
		
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);			
	}
	
}


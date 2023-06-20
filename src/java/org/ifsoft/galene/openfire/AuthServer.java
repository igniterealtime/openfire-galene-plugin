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
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.muc.*;

import org.xmpp.packet.*;
import net.sf.json.*;

public class AuthServer extends HttpServlet {
    private static final Logger Log = LoggerFactory.getLogger(AuthServer.class);

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String body = request.getReader().lines().collect(Collectors.joining());
        Log.info("AuthServer post\n" + body);
		
		try {
			JSONObject json = new JSONObject(body);
			String username = json.getString("username");
			String password = json.getString("password");
			
			if (!"".equals(username) && !"".equals(password) && !"undefined".equals(username) && !"undefined".equals(password) && !"null".equals(username) && !"null".equals(password)) {
				try {
					AuthFactory.authenticate(username, password);
				} catch (org.jivesoftware.openfire.auth.UnauthorizedException ex) {
					response.setStatus(HttpServletResponse.SC_FORBIDDEN);
					return;					
				}

				int perm = 0;				
				String location = json.getString("location");	
				
				int pos1 = location.indexOf("?room=");
				int pos2 = location.indexOf("&");
				int pos3 = location.indexOf("/group/");				
				
				if ((pos1 > -1 && pos2 > -1) || pos3 > -1) {
					String roomPath, room;
					
					if (pos1 > -1 && pos2 > -1) {	// "https://localhost:7443/galene/?room=lobby&username=dele&password=Welcome123"
						roomPath = location.substring(pos1 + 6, pos2);					
						room = roomPath.split("/")[0];
						String parts[] = location.split("/");
						location = parts[0] + "//" + parts[2] + "/group/" + roomPath + "/";
						
					} else {						// "https://localhost:7443/group/lobby/"
						String parts[] = location.split("/");	
						room = 	parts[4];					
					}
					
					Log.info("AuthServer location\n" + location);
					
					MUCRoom mucRoom = null;
					
					if ("public".equals(room)) {
						response.setStatus(HttpServletResponse.SC_NO_CONTENT);	
						return;						
					} else {
						mucRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(room);									
					}
					
					if (mucRoom == null) {
						response.setStatus(HttpServletResponse.SC_FORBIDDEN);
						return;
					}

					for (JID jid : mucRoom.getMembers()) {				
						if (jid.getNode().equals(username)) perm = 1;
					}
					
					for (JID jid : mucRoom.getAdmins()) {				
						if (jid.getNode().equals(username)) perm = 2;
					}	

					for (JID jid : mucRoom.getOwners()) {				
						if (jid.getNode().equals(username)) perm = 3;
					}								
				}				
				
				JSONArray permissions = new JSONArray();
				
				if (perm == 3) {
					permissions.put(0, "record");	
					permissions.put(1, "op");							
					permissions.put(2, "present");							
				} 
				else

				if (perm == 2) {
					permissions.put(0, "op");							
					permissions.put(1, "present");							
				}	

				else

				if (perm == 1) {						
					permissions.put(0, "present");							
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
				response.setStatus(HttpServletResponse.SC_NO_CONTENT);
			}			
			
		} catch (Exception e) {
			Log.error("AuthServer post", e);
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);			
		}
	}
	
}


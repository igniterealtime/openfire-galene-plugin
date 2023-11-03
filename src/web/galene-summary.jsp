<%@ page import="org.jivesoftware.util.*,
				 org.ifsoft.galene.openfire.*,
                 java.util.*,
				 net.sf.json.*,
                 java.net.URLEncoder"                 
    errorPage="error.jsp"
%>
<%@ page import="org.xmpp.packet.JID" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<% 
    Galene plugin = Galene.self;
	plugin.setupGaleneFiles(); 	// refresh galene
		
	JSONArray connections = new JSONArray();
	String json = Galene.self.getJson("/stats.json");
	
	if (json != null && !"".equals(json)) {
		connections = new JSONArray(json);
	}
	
	int sessionCount = connections.length();	
%>
<html>
    <head>
        <title><fmt:message key="config.page.summary"/></title>
        <meta name="pageID" content="galene-summary"/>
    </head>
    <body>

<%
	String service_url = JiveGlobals.getProperty("galene.url",  plugin.getUrl());
	String adminUsername = JiveGlobals.getProperty("galene.username", "sfu-admin");
	String adminPassword = JiveGlobals.getProperty("galene.password", "sfu-admin");	   
%>	

<% if (request.getParameter("deletesuccess") != null) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="galene.session.expired" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } 

	for (int s=0; s<connections.length(); s++) {
		JSONObject connection = connections.getJSONObject(s);
		String roomName = connection.getString("name");
		String url = service_url + "/galene/?room=" + roomName + "&username=" + adminUsername + "&password=" + adminPassword;		

%>

<div class='jive-contentBoxHeader'><a target='_blank' href='<%= url %>'><%= roomName %></a></div>		
<div class='jive-contentBox'>
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th nowrap><fmt:message key="galene.client.id" /></th>
        <th nowrap><fmt:message key="galene.client.stream.id" /></th>
        <th nowrap><fmt:message key="galene.client.type" /></th>		
        <th nowrap><fmt:message key="galene.client.track.id" /></th>	
        <th nowrap><fmt:message key="galene.client.track.bitrate" /></th>           
        <th nowrap><fmt:message key="galene.client.track.maxBitrate" /></th>
        <th nowrap><fmt:message key="galene.client.track.loss" /></th>
        <th nowrap><fmt:message key="galene.client.track.jitter" /></th>   
		<th nowrap><fmt:message key="galene.client.expire" /></th>    		
    </tr>
</thead>
<tbody>

<% 
    if (!connection.has("clients")) {
%>
    <tr>
        <td align="center" colspan="9">
            <fmt:message key="galene.summary.no.clients" />
        </td>
    </tr>

</tbody>
</table>
</div>
<%	
		continue;
    }
	
    int i = 0;
	JSONArray clients = connection.getJSONArray("clients");
	
	for (int t=0; t<clients.length(); t++) {
		JSONObject client = clients.getJSONObject(t);
		JSONArray ups = new JSONArray();
		JSONArray downs = new JSONArray();	
				
		String id = client.getString("id");
		
		if (GaleneIQHandler.clients.containsKey(id)) {
			id = GaleneIQHandler.clients.get(id).getFullId();
		}		
		
		if (!client.has("up") && !client.has("down")) {
%>
			<tr>
				<td width="10%" valign="center">
					<%= id %>
				</td>	
				<td align="center" colspan="8">
				</td>
			</tr>
<%	
			continue;	
		}
		
		if (client.has("up")) ups = client.getJSONArray("up");
		if (client.has("down")) downs = client.getJSONArray("down");		

		int iterations = ups.length() + downs.length();

		for (int c=0; c<iterations; c++) {
			for (int r=0; r<2; r++) {			
				JSONArray data = downs;
				int k = c - ups.length();
				String streamType = "down";
				
				if (c < ups.length() && ups.length() > 0) {
					k = c;
					data = ups;
					streamType = "up";
				}
				
				i++;
%>
				<tr class="jive-<%= (((i%2)==0) ? "even" : "odd") %>">
					
					<td width="10%" valign="left">
						<%= (c == 0 && r ==0) ? id : "" %>
					</td>
						
					
					<td width="10%" align="center">
						<%= (r == 0) ? data.getJSONObject(k).getString("id") : "" %>
					</td>			
					
					<td width="10%" align="center">
						<%= (r == 0) ? streamType : "" %>
					</td>					
					
					<td width="10%" align="center">
						<%= (r == 0) ? "audio" : "video" %>
					</td> 					
<%				
				if (r >= data.getJSONObject(k).getJSONArray("tracks").length()) {
%>	
					<td align="center" colspan="5">
					</td>
<%	
					continue;
				}
%>		
					<td width="10%" align="center">
						<%= data.getJSONObject(k).getJSONArray("tracks").getJSONObject(r).getString("bitrate") %>
					</td>  	 
					
					<td width="10%" align="center">
						<%= data.getJSONObject(k).getJSONArray("tracks").getJSONObject(r).getString("maxBitrate") %>

					</td>
					
					<td width="10%" align="center">		
						<%= data.getJSONObject(k).getJSONArray("tracks").getJSONObject(r).getString("loss") %>
					</td>
					
					<td width="10%" align="center">
						<%  
							String jitter = "";
							JSONObject object = data.getJSONObject(k).getJSONArray("tracks").getJSONObject(r);
							if (object.has("jitter")) jitter = object.getString("jitter");
						%>
						<%= jitter %>

					</td>  
					
					<td width="5%" align="center" style="border-right:1px #ccc solid;">
<%
			if (c == 0 && r == 0) {
%>
						<a href="galene-expire.jsp?client=<%= URLEncoder.encode(client.getString("id"), "UTF-8") %>&group=<%= URLEncoder.encode(connection.getString("name"), "UTF-8") %>" title="<fmt:message key="galene.client.expire" />">
							<img src="images/delete-16x16.gif" width="16" height="16" border="0" alt="">
						</a>
<%
			}
%>
					</td>
				</tr>
<%
			}		
		}
      }
%>
</tbody>
</table>
</div>
<%		  
	}
%>
</body>
</html>
<%@ taglib uri="admin" prefix="admin" %>
<admin:FlashMessage/> 
<%@ page import="java.util.Hashtable"%>
<%@ page import="org.xmpp.packet.JID" %>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="org.jivesoftware.util.Log"%>
<%@ page import="org.jivesoftware.openfire.XMPPServer, org.ifsoft.galene.openfire.Galene, org.jivesoftware.util.ParamUtils,org.jivesoftware.openfire.*,java.util.HashMap,java.util.Map,org.jivesoftware.util.*,org.apache.commons.text.StringEscapeUtils" errorPage="error.jsp"%>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt"%>
<%
    boolean update = request.getParameter("update") != null;
    String errorMessage = null;
    final JID roomJID = new JID(ParamUtils.getParameter(request,"roomJID"));
    
    Galene plugin = Galene.self;
    Map<String, String> properties = null;

    if (roomJID != null) {
        try {      
            properties = plugin.getGroupChatProperties(roomJID);
        } catch (Exception e) {
        }
    }
	
    if (update) { 	
        String enabled = request.getParameter("enabled");
        String federationEnabled = request.getParameter("federationEnabled");
		
        properties.put("galene.enabled", (enabled != null && enabled.equals("on")) ? "true": "false");  
        properties.put("galene.federation.enabled", (federationEnabled != null && federationEnabled.equals("on")) ? "true": "false");  
		
		plugin.writeGaleneGroupFile(roomJID);
	}
%>



<html>
<head>
<title>SFU Properties</title>
<meta name="subPageID" content="galene-muc" />
<meta name="extraParams" content="<%= "roomJID="+URLEncoder.encode(roomJID.toBareJID(), "UTF-8") %>"/>
</head>
<body>
<% if (errorMessage != null) { %>
<div class="error">
    <%= errorMessage%>
</div>
<br/>
<% } %>

<form action="galene-muc.jsp" method="get">
<div class='jive-contentBoxHeader'><fmt:message key="config.page.sfu.for" /><%= " " + roomJID  %></div>		
<div class='jive-contentBox'>
<table cellpadding="0" cellspacing="0" border="0" width="100%">
	<tr>
		<td nowrap  colspan="2">
			<input type="checkbox" name="enabled"<%= (properties.get("galene.enabled").equals("true")) ? " checked" : "" %>>
			<fmt:message key="config.page.configuration.enabled" />       
		</td>  
	</tr>
	<tr>
		<td nowrap  colspan="2">
			<input type="checkbox" name="federationEnabled"<%= (properties.get("galene.federation.enabled").equals("true")) ? " checked" : "" %>>
			<fmt:message key="config.page.enable.federation" />       
		</td>  
	</tr>	
</table>
</div>
<input type="hidden" name="roomJID" value="<%= roomJID.toBareJID() %>" />
<input type="submit" name="update" value="<fmt:message key="config.page.configuration.submit" />">
</form>
</body>
</html>


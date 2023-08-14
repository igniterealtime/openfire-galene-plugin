<%@ page import="java.util.*" %>
<%@ page import="org.ifsoft.galene.openfire.*" %>
<%@ page import="org.jivesoftware.openfire.*" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%

    boolean update = request.getParameter("update") != null;
    String errorMessage = null;

    // Get handle on the plugin
    Galene plugin = Galene.self;

    if (update)
    {    
        String username = request.getParameter("username");     
        JiveGlobals.setProperty("galene.username", username);     

        String password = request.getParameter("password");     
        JiveGlobals.setProperty("galene.password", password);   
        
        String port = request.getParameter("port");     
        JiveGlobals.setProperty("galene.port", port);   
		
        String portRangeMin = request.getParameter("port_range_min");     
        JiveGlobals.setProperty("galene.port.range.min", portRangeMin); 
		
        String portRangeMax = request.getParameter("port_range_max");     
        JiveGlobals.setProperty("galene.port.range.max", portRangeMax); 		
        
        String ipaddr = request.getParameter("ipaddr");     
        JiveGlobals.setProperty("galene.ipaddr", ipaddr);   
        
        String turnport = request.getParameter("turnport");     
        JiveGlobals.setProperty("galene.turn.port", turnport);   
        
        String turnipaddr = request.getParameter("turnipaddr");     
        JiveGlobals.setProperty("galene.turn.ipaddr", turnipaddr);         
        
        String url = request.getParameter("url");     
        JiveGlobals.setProperty("galene.url", url);         
        
        String muc_enabled = request.getParameter("muc_enabled");
        JiveGlobals.setProperty("galene.muc.enabled", (muc_enabled != null && muc_enabled.equals("on")) ? "true": "false");     

        String enabled = request.getParameter("enabled");
        JiveGlobals.setProperty("galene.enabled", (enabled != null && enabled.equals("on")) ? "true": "false");  

		plugin.setupGaleneFiles(); 		
    }

    String service_url = plugin.getUrl();    

%>
<html>
<head>
   <title><fmt:message key="config.page.settings" /></title>
   <meta name="pageID" content="galene-settings"/>
</head>
<body>
<% if (errorMessage != null) { %>
<div class="error">
    <%= errorMessage%>
</div>
<br/>
<% } %>

<div class="jive-table">
<form action="galene-settings.jsp" method="post">
    <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead> 
            <tr>
                <th colspan="2"><fmt:message key="config.page.settings.description"/></th>
            </tr>
            </thead>
            <tbody>  
            <tr>
                <td nowrap  colspan="2">
                    <input type="checkbox" name="enabled"<%= (JiveGlobals.getProperty("galene.enabled", "true").equals("true")) ? " checked" : "" %>>
                    <fmt:message key="config.page.configuration.enabled" />       
                </td>  
            </tr>
            <tr>
                <td nowrap  colspan="2">
                    <input type="checkbox" name="muc_enabled"<%= (JiveGlobals.getProperty("galene.muc.enabled", "false").equals("true")) ? " checked" : "" %>>
                    <fmt:message key="config.page.configuration.muc.enabled" />       
                </td>  
            </tr>			
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.username"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="username" required
                       value="<%= JiveGlobals.getProperty("galene.username", "sfu-admin") %>">
                </td>
            </tr>   
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.password"/>
                </td>
                <td><input type="password" size="50" maxlength="100" name="password" required
                       value="<%= JiveGlobals.getProperty("galene.password", "sfu-admin") %>">
                </td>
            </tr>              
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.ipaddr"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="ipaddr" required
                       value="<%= JiveGlobals.getProperty("galene.ipaddr", plugin.getIpAddress()) %>">
                </td>                               
            </tr>                   
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.port"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="port" required
                       value="<%= JiveGlobals.getProperty("galene.port", plugin.getPort()) %>">
                </td>                               
            </tr>  
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.port.range.min"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="port_range_min" required
                       value="<%= JiveGlobals.getProperty("galene.port.range.min", plugin.getPortRangeMin()) %>">
                </td>                               
            </tr>  	
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.port.range.max"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="port_range_max" required
                       value="<%= JiveGlobals.getProperty("galene.port.range.max", plugin.getPortRangeMax()) %>">
                </td>                               
            </tr>			
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.turn.ipaddr"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="turnipaddr" required
                       value="<%= JiveGlobals.getProperty("galene.turn.ipaddr", plugin.getIpAddress()) %>">
                </td>                               
            </tr>                   
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.turn.port"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="turnport" required
                       value="<%= JiveGlobals.getProperty("galene.turn.port", plugin.getTurnPort()) %>">
                </td>                               
            </tr>            
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.url"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="url" required
                       value="<%= JiveGlobals.getProperty("galene.url", service_url) %>">
                </td>                               
            </tr>            
            </tbody>
        </table>
    </p>
   <p>
        <table class="jive-table" cellpadding="0" cellspacing="0" border="0" width="100%">
            <thead> 
            <tr>
                <th colspan="2"><fmt:message key="config.page.configuration.save.title"/></th>
            </tr>
            </thead>
            <tbody>         
            <tr>
                <th colspan="2"><input type="submit" name="update" value="<fmt:message key="config.page.configuration.submit" />">&nbsp;&nbsp;<fmt:message key="config.page.configuration.restart.warning"/></th>
            </tr>       
            </tbody>            
        </table> 
    </p>
</form>
</div>
</body>
</html>

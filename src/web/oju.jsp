<%@ page import="java.util.*" %>
<%@ page import="org.ifsoft.oju.openfire.*" %>
<%@ page import="org.jivesoftware.openfire.*" %>
<%@ page import="org.jivesoftware.util.*" %>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%

    boolean update = request.getParameter("update") != null;
    String errorMessage = null;

    // Get handle on the plugin
    Oju plugin = Oju.self;

    if (update)
    {    
        String username = request.getParameter("username");     
        JiveGlobals.setProperty("oju.username", username);     

        String password = request.getParameter("password");     
        JiveGlobals.setProperty("oju.password", password);   
        
        String port = request.getParameter("port");     
        JiveGlobals.setProperty("oju.port", port);   
        
        String ipaddr = request.getParameter("ipaddr");     
        JiveGlobals.setProperty("oju.ipaddr", ipaddr);   
        
        String turnport = request.getParameter("turnport");     
        JiveGlobals.setProperty("oju.turn.port", turnport);   
        
        String turnipaddr = request.getParameter("turnipaddr");     
        JiveGlobals.setProperty("oju.turn.ipaddr", turnipaddr);         
        
        String url = request.getParameter("url");     
        JiveGlobals.setProperty("oju.url", url);         
        
        String enabled = request.getParameter("enabled");
        JiveGlobals.setProperty("oju.enabled", (enabled != null && enabled.equals("on")) ? "true": "false");        
    }

    String service_url = plugin.getUrl();    

%>
<html>
<head>
   <title><fmt:message key="config.page.settings" /></title>
   <meta name="pageID" content="oju-settings"/>
</head>
<body>
<% if (errorMessage != null) { %>
<div class="error">
    <%= errorMessage%>
</div>
<br/>
<% } %>

<p>
    <fmt:message key="config.page.connectivity.description" />&nbsp;<a target="_blank" href="<%= service_url %>"><%= service_url %></a>
</p> 
<div class="jive-table">
<form action="oju.jsp" method="post">
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
                    <input type="checkbox" name="enabled"<%= (JiveGlobals.getProperty("oju.enabled", "true").equals("true")) ? " checked" : "" %>>
                    <fmt:message key="config.page.configuration.enabled" />       
                </td>  
            </tr>
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.username"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="username" required
                       value="<%= JiveGlobals.getProperty("oju.username", "administrator") %>">
                </td>
            </tr>   
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.password"/>
                </td>
                <td><input type="password" size="50" maxlength="100" name="password" required
                       value="<%= JiveGlobals.getProperty("oju.password", "admin") %>">
                </td>
            </tr>              
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.ipaddr"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="ipaddr" required
                       value="<%= JiveGlobals.getProperty("oju.ipaddr", plugin.getIpAddress()) %>">
                </td>                               
            </tr>                   
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.port"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="port" required
                       value="<%= JiveGlobals.getProperty("oju.port", plugin.getPort()) %>">
                </td>                               
            </tr>  
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.turn.ipaddr"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="turnipaddr" required
                       value="<%= JiveGlobals.getProperty("oju.turn.ipaddr", plugin.getIpAddress()) %>">
                </td>                               
            </tr>                   
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.turn.port"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="turnport" required
                       value="<%= JiveGlobals.getProperty("oju.turn.port", plugin.getTurnPort()) %>">
                </td>                               
            </tr>            
            <tr>
                <td align="left" width="150">
                    <fmt:message key="config.page.configuration.url"/>
                </td>
                <td><input type="text" size="50" maxlength="100" name="url" required
                       value="<%= JiveGlobals.getProperty("oju.url", service_url) %>">
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

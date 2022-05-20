<%@ page import="org.jivesoftware.util.*,
				 org.ifsoft.galene.openfire.Galene,
                 java.net.URLEncoder"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager" />
<% webManager.init(request, response, session, application, out ); %>

<% 
	String client = ParamUtils.getParameter(request,"client");
	String stream = ParamUtils.getParameter(request,"stream");
    	

	// Done, so redirect
	response.sendRedirect("galene-summary.jsp?deletesuccess=true");
	return;
%>
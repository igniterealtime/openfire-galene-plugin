package org.ifsoft.oju.openfire;

import java.io.File;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import java.nio.file.*;
import java.nio.charset.Charset;
import java.security.Security;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.sasl.AnonymousSaslServer;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.StringUtils;

import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.servlets.*;
import org.eclipse.jetty.servlet.*;
import org.eclipse.jetty.websocket.servlet.*;
import org.eclipse.jetty.websocket.server.*;
import org.eclipse.jetty.websocket.server.pathmap.ServletPathSpec;
import org.eclipse.jetty.webapp.WebAppContext;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.util.security.*;
import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.*;

import java.lang.reflect.*;
import java.util.*;

import org.jitsi.util.OSUtils;
import de.mxro.process.*;
import org.ifsoft.websockets.*;
import net.sf.json.*;
import org.xmpp.packet.*;

public class Oju implements Plugin, PropertyEventListener, ProcessListener, MUCEventListener
{
    private static final Logger Log = LoggerFactory.getLogger(Oju.class);
    private XProcess ojuThread = null;
    private String ojuExePath = null;
    private String ojuHomePath = null;
    private String ojuRoot = null;
    private ExecutorService executor;
    private ServletContextHandler ojuContext;
    private WebAppContext jspService;
    private ServletContextHandler ojuWsContext = null;
    private Cache muc_properties;	

    public static Oju self;	

    public void destroyPlugin()
    {
        PropertyEventDispatcher.removeListener(this);
        MUCEventDispatcher.removeListener(this);

        try {
            if (executor != null)  executor.shutdown();
            if (ojuThread != null) ojuThread.destory();
            if (ojuContext != null) HttpBindManager.getInstance().removeJettyHandler(ojuContext);
            if (jspService != null) HttpBindManager.getInstance().removeJettyHandler(jspService);

            if (ojuWsContext != null)
            {
                ojuWsContext.destroy();
                HttpBindManager.getInstance().removeJettyHandler(ojuWsContext);
            }

            Log.info("gaelene terminated");
        }
        catch (Exception e) {
            Log.error("Oju destroyPlugin", e);
        }
    }

    public void initializePlugin(final PluginManager manager, final File pluginDirectory)
    {
		muc_properties = CacheFactory.createLocalCache("MUC Room Properties");	
		
        PropertyEventDispatcher.addListener(this);
        MUCEventDispatcher.addListener(this);

        checkNatives(pluginDirectory);
        executor = Executors.newCachedThreadPool();

        loadWebSocket();
        startJSP(pluginDirectory);
        startGoProcesses(pluginDirectory);
        self = this;
        Log.info("gaelene initiated");
    }

    public static String getPort()
    {
        return "6060";
    }

    public static String getTurnPort()
    {
        return "10014";
    }

    public String getHome()
    {
        return ojuHomePath;
    }

    public static String getUrl()
    {
        return "https://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + JiveGlobals.getProperty("httpbind.port.secure", "7443");
    }

    public static String getIpAddress()
    {
        String ourHostname = XMPPServer.getInstance().getServerInfo().getHostname();
        String ourIpAddress = "127.0.0.1";

        try {
            ourIpAddress = InetAddress.getByName(ourHostname).getHostAddress();
        } catch (Exception e) {

        }

        return ourIpAddress;
    }

    public void onOutputLine(final String line)
    {
        Log.info("onOutputLine " + line);
    }

    public void onProcessQuit(int code)
    {
        Log.info("onProcessQuit " + code);
    }

    public void onOutputClosed() {
        Log.error("onOutputClosed");
    }

    public void onErrorLine(final String line)
    {
        Log.debug(line);
    }

    public void onError(final Throwable t)
    {
        Log.error("Thread error", t);
    }

    private void startJSP(File pluginDirectory)
    {
        jspService = new WebAppContext(null, pluginDirectory.getPath() + "/classes/jsp",  "/jsp");
        jspService.setClassLoader(this.getClass().getClassLoader());
        jspService.getMimeTypes().addMimeMapping("wasm", "application/wasm");

		boolean anonLogin = AnonymousSaslServer.ENABLED.getValue();

		if (!anonLogin)
		{
			SecurityHandler securityHandler = basicAuth("oju");
			jspService.setSecurityHandler(securityHandler);
		}

        final List<ContainerInitializer> initializers = new ArrayList<>();
        initializers.add(new ContainerInitializer(new JettyJasperInitializer(), null));
        jspService.setAttribute("org.eclipse.jetty.containerInitializers", initializers);
        jspService.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());

        Log.info("Oju jsp service enabled");
        HttpBindManager.getInstance().addJettyHandler(jspService);
    }

    protected void loadWebSocket()
    {
        Log.info( "Initializing web socket" );

        ojuWsContext = new ServletContextHandler(null, "/galene-ws", ServletContextHandler.SESSIONS);

        try {					
            boolean anonLogin = AnonymousSaslServer.ENABLED.getValue();

            if (!anonLogin)
            {
                SecurityHandler securityHandler = basicAuth("oju");
                ojuWsContext.setSecurityHandler(securityHandler);
            }			
            WebSocketUpgradeFilter wsfilter = WebSocketUpgradeFilter.configureContext(ojuWsContext);
            wsfilter.getFactory().getPolicy().setIdleTimeout(60 * 60 * 1000);
            wsfilter.getFactory().getPolicy().setMaxTextMessageSize(64000000);
            wsfilter.addMapping(new ServletPathSpec("/*"), new OjuSocketCreator());			

        } catch (Exception e) {
            Log.error("loadWebSocket", e);
        }

        HttpBindManager.getInstance().addJettyHandler(ojuWsContext);

        Log.debug( "Initialized web socket");
    }

    public static class OjuSocketCreator implements WebSocketCreator
    {
        @Override public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp)
        {
            String ipaddr = getIpAddress();
            String ojuPort = JiveGlobals.getProperty("galene.port", getPort());

            HttpServletRequest request = req.getHttpServletRequest();
            String query = request.getQueryString();
            List<String> protocols = new ArrayList<String>();

            for (String subprotocol : req.getSubProtocols())
            {
                Log.info("WSocketCreator found protocol " + subprotocol);
                resp.setAcceptedSubProtocol(subprotocol);
                protocols.add(subprotocol);
            }

            String path = "/ws";
            if (query != null) path += "?" + query;

            Log.info("JvbSocketCreator " + path);

            String url = "ws://localhost:" + ojuPort + path;

            ProxyWebSocket socket = null;
            ProxyConnection proxyConnection = new ProxyConnection(URI.create(url), protocols, 10000);

            socket = new ProxyWebSocket();
            socket.setProxyConnection(proxyConnection);
            return socket;
        }
    }

    private void startGoProcesses(File pluginDirectory)
    {
        boolean ojuEnabled = JiveGlobals.getBooleanProperty("galene.enabled", true);

        if (ojuExePath != null && ojuEnabled)
        {
            createAdminUser();
            setupOjuFiles();

            ojuContext = new ServletContextHandler(null, "/", ServletContextHandler.SESSIONS);
            ojuContext.setClassLoader(this.getClass().getClassLoader());
            ServletHolder proxyServlet = new ServletHolder(ProxyServlet.Transparent.class);
            String ojuUrl = "http://" + JiveGlobals.getProperty("galene.ipaddr", getIpAddress()) + ":" + JiveGlobals.getProperty("galene.port", getPort());
            String turn = JiveGlobals.getProperty("galene.turn.ipaddr", getIpAddress()) + ":" + JiveGlobals.getProperty("galene.turn.port", "10014");
            proxyServlet.setInitParameter("proxyTo", ojuUrl);
            proxyServlet.setInitParameter("prefix", "/");
            ojuContext.addServlet(proxyServlet, "/*");
            HttpBindManager.getInstance().addJettyHandler(ojuContext);

            String params = "--insecure=true --http=:" + getPort() + " --turn=" + turn;
            ojuThread = Spawn.startProcess(ojuExePath + " " + params, new File(ojuHomePath), this);

            Log.info("Oju enabled " + ojuExePath);

        } else {
            Log.info("Oju disabled");
        }
    }

    private void checkNatives(File pluginDirectory)
    {
        try
        {
            ojuRoot = JiveGlobals.getHomeDirectory() + File.separator + "oju";

            File ojuRootPath = new File(ojuRoot);

            if (!ojuRootPath.exists())
            {
                ojuRootPath.mkdirs();
            }

            ojuHomePath = pluginDirectory.getAbsolutePath() + File.separator + "classes";

            if(OSUtils.IS_LINUX64)
            {
                ojuHomePath = ojuHomePath + File.separator + "linux-64";
                ojuExePath = ojuHomePath + File.separator + "galene";
                makeFileExecutable(ojuExePath);
            }
            else if(OSUtils.IS_WINDOWS64)
            {
                ojuHomePath = ojuHomePath + File.separator + "win-64";
                ojuExePath = ojuHomePath + File.separator + "galene.exe";
                makeFileExecutable(ojuExePath);				
            }
            else if(OSUtils.IS_WINDOWS32)
            {
                ojuHomePath = ojuHomePath + File.separator + "win-32";
                ojuExePath = ojuHomePath + File.separator + "galene.exe";
                makeFileExecutable(ojuExePath);

            } else {
                Log.error("checkNatives unknown OS " + pluginDirectory.getAbsolutePath());
                return;
            }
        }
        catch (Exception e)
        {
            Log.error("checkNatives error", e);
        }
    }

    private void makeFileExecutable(String path)
    {
        File file = new File(path);
        file.setReadable(true, true);
        file.setWritable(true, true);
        file.setExecutable(true, true);
        Log.info("checkNatives galene executable path " + path);
    }

    private void setupOjuFiles()
    {
        String iniFileName = ojuHomePath + "/data/config.json";
        List<String> lines = new ArrayList<String>();
        Log.info("Creating " + iniFileName);
		
        // create galene global config.json file

        JSONObject json = new JSONObject();	
		
		String hostname = JiveGlobals.getProperty("galene.hostname", XMPPServer.getInstance().getServerInfo().getHostname()) + ":" + JiveGlobals.getProperty("galene.port", getPort());
		// causes a redirect
		//json.put("canonicalHost", JiveGlobals.getProperty("galene.username", hostname));	
		
        JSONArray admins = new JSONArray();	
        JSONObject admin = new JSONObject();			
        admin.put("username", JiveGlobals.getProperty("galene.username", "administrator"));
		admin.put("password", JiveGlobals.getProperty("galene.password", "administrator"));	
		admins.put(0, admin);
		json.put("admin", admins);
		
		lines.add(json.toString());

        try
        {
            Path file = Paths.get(iniFileName);
            Files.write(file, lines, Charset.forName("UTF-8"));
        }
        catch ( Exception e )
        {
            Log.error( "Unable to write file " + iniFileName, e );
        }

        // create galene group json files

        String service = "conference";
        List<MUCRoom> rooms = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(service).getChatRooms();

        for (MUCRoom room : rooms)
        {
            if (room.isPersistent())
            {
                setupOjuFile(room);
            }
        }
    }


    private void setupOjuFile(MUCRoom room)
    {
        Log.debug("setupOjuFile " + room.getJID());

        String roomName = room.getJID().getNode();
        String password = room.getPassword();
		Map<String, String> properties = (Map<String, String>) muc_properties.get(room.getJID().toString());
		
		if (properties == null)
		{
			properties = new MUCRoomProperties(room.getID());
			muc_properties.put(room.getJID().toString(), properties);
		}		

        if (!properties.containsKey("galene.owner.password")) properties.put("galene.owner.password", StringUtils.randomString(40));
        if (!properties.containsKey("galene.admin.password")) properties.put("galene.admin.password", StringUtils.randomString(40));

        JSONObject json = new JSONObject();

        JSONArray op = new JSONArray();
        int op_kt = 0;

        JSONArray presenter = new JSONArray();
        int presenter_kt = 0;

        JSONArray other = new JSONArray();
        int other_kt = 0;

        for (JID jid : room.getOwners())
        {
            Log.debug("setupOjuFile owner " + jid + " " + room.getJID());
            JSONObject owner = new JSONObject();
            owner.put("username", jid.getNode());
            owner.put("password", properties.get("galene.owner.password"));
            op.put(op_kt++, owner);
        }

        json.put("op", op);

        for (JID jid : room.getAdmins())
        {
            Log.debug("setupOjuFile admin " + jid + " " + room.getJID());
            JSONObject admin = new JSONObject();
            admin.put("username", jid.getNode());
            admin.put("password", properties.get("galene.admin.password"));
            presenter.put(presenter_kt++, admin);
        }

        if (presenter_kt == 0) presenter.put(0, new JSONObject()); // anybody is presenter
        json.put("presenter", presenter);

        for (JID jid : room.getMembers())
        {
            Log.debug("setupOjuFile member " + jid + " " + room.getJID());
            JSONObject member = new JSONObject();
            member.put("username", jid.getNode());

            if (password != null && !password.isEmpty())
            {
                member.put("password", password);  ;
            }
            other.put(other_kt++, member);
        }

        if (other_kt == 0 && !room.isMembersOnly()) other.put(0, new JSONObject());  // anybody is member
        json.put("other", other);

        json.put("public", room.isPublicRoom());
        json.put("description", room.getDescription());
        json.put("contact", room.getName());
        json.put("comment", room.getSubject());
        json.put("allow-recording", room.isLogEnabled());
        json.put("allow-anonymous", !room.isMembersOnly() && (password == null || password.isEmpty()));
        json.put("allow-subgroups", room.canOccupantsInvite());
        json.put("max-clients", room.getMaxUsers());

        String iniFileName = ojuHomePath + "/groups/" + roomName + ".json";
        List<String> lines = new ArrayList<String>();
        lines.add(json.toString());

        Log.info("Creating " + iniFileName);

        try
        {
            Path file = Paths.get(iniFileName);
            Files.write(file, lines, Charset.forName("UTF-8"));
        }
        catch ( Exception e )
        {
            Log.error( "Unable to write file " + iniFileName, e );
        }
    }

    private void createAdminUser()
    {
        final UserManager userManager = XMPPServer.getInstance().getUserManager();
        final String administrator = JiveGlobals.getProperty("galene.username", "administrator");

        if ( !userManager.isRegisteredUser( administrator ) )
        {
            Log.info( "No administrator user detected. Generating one." );

            try
            {
                String password = StringUtils.randomString(40);
                JiveGlobals.setProperty("galene.password", password);
                userManager.createUser(administrator, password, "Administrator (generated)", null);
            }
            catch ( Exception e )
            {
                Log.error( "Unable to provision an administrator user.", e );
            }
        }
    }

    private static final SecurityHandler basicAuth(String realm) {

        OjuLoginService l = new OjuLoginService(realm);
        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{realm, "webapp-owner", "webapp-contributor"});
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName(realm);
        csh.addConstraintMapping(cm);
        csh.setLoginService(l);

        return csh;
    }

    // -------------------------------------------------------
    //
    //  MUCEventListener
    //
    // -------------------------------------------------------

    public void roomCreated(JID roomJID)
    {
        MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomJID.getNode());

        if (room != null && room.isPersistent())
        {
            setupOjuFile(room);
        }
    }

    public void roomDestroyed(JID roomJID)
    {

    }

    public void occupantJoined(JID roomJID, JID user, String nickname)
    {

    }

    public void occupantLeft(JID roomJID, JID user, String nickname)
    {

    }

	public void occupantNickKicked(JID roomJID, String nickname)
	{
		
	}
	
    public void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname)
    {

    }

    public void messageReceived(JID roomJID, JID user, String nickname, Message message)
    {

    }

    public void roomSubjectChanged(JID roomJID, JID user, String newSubject)
    {

    }

    public void privateMessageRecieved(JID a, JID b, Message message)
    {

    }


    //-------------------------------------------------------
    //
    //  PropertyEventListener
    //
    //-------------------------------------------------------


    public void propertySet(String property, Map params)
    {

    }

    public void propertyDeleted(String property, Map<String, Object> params)
    {

    }

    public void xmlPropertySet(String property, Map<String, Object> params) {

    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {

    }

}
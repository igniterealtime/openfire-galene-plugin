package org.ifsoft.galene.openfire;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import java.nio.file.*;
import java.nio.charset.*;
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

import org.eclipse.jetty.ee8.webapp.WebAppContext;
import org.eclipse.jetty.ee8.servlet.*;
import org.eclipse.jetty.ee8.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.ee8.websocket.servlet.*;
import org.eclipse.jetty.ee8.websocket.server.*;
import org.eclipse.jetty.ee8.websocket.server.config.*;

import org.eclipse.jetty.ee8.proxy.ProxyServlet;

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

public class Galene implements Plugin, PropertyEventListener, ProcessListener, MUCEventListener
{
    private static final Logger Log = LoggerFactory.getLogger(Galene.class);
	private String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    private XProcess galeneThread = null;
    private String galeneExePath = null;
    private String galeneHomePath = null;
    private Path galeneRoot;
    private ExecutorService executor;
    private WebAppContext jspService;
    private ServletContextHandler galeneContext;
    private ServletContextHandler galeneWsContext;	
    private GaleneIQHandler galeneIQHandler;
    private OlMeetIQHandler olMeetIQHandler;	
	private ProxyConnection adminConnection;
    private Cache muc_properties;		
	
    public static Galene self;	

    public void destroyPlugin()
    {
        PropertyEventDispatcher.removeListener(this);
        MUCEventDispatcher.removeListener(this);		

        try {
			if (adminConnection != null) adminConnection.disconnect();
            if (executor != null)  executor.shutdown();
            if (galeneThread != null) galeneThread.destory();
            if (jspService != null) HttpBindManager.getInstance().removeJettyHandler(jspService);
            if (galeneContext != null) HttpBindManager.getInstance().removeJettyHandler(galeneContext);	

			if (galeneWsContext != null) {
				HttpBindManager.getInstance().removeJettyHandler(galeneWsContext);
				galeneWsContext.destroy();
			}			

			galeneIQHandler.stopHandler();		
            XMPPServer.getInstance().getIQRouter().removeHandler(galeneIQHandler);
            galeneIQHandler = null;		

            XMPPServer.getInstance().getIQRouter().removeHandler(olMeetIQHandler);
            olMeetIQHandler = null;					

            Log.info("gaelene terminated");
        }
        catch (Exception e) {
            Log.error("Galene destroyPlugin", e);
        }
    }

    public void initializePlugin(final PluginManager manager, final File pluginDirectory) {	
		muc_properties = CacheFactory.createLocalCache("MUC Room Properties");	
		
        PropertyEventDispatcher.addListener(this);
        MUCEventDispatcher.addListener(this);
		
        checkNatives(pluginDirectory);
        executor = Executors.newCachedThreadPool();
        startJSP(pluginDirectory);
        startGoProcesses(pluginDirectory);
        self = this;	
		
		galeneIQHandler = new GaleneIQHandler();
		galeneIQHandler.startHandler();		
		XMPPServer.getInstance().getIQRouter().addHandler(galeneIQHandler);	
		XMPPServer.getInstance().getIQDiscoInfoHandler().addServerFeature("urn:xmpp:sfu:galene:0");			
		
		olMeetIQHandler = new OlMeetIQHandler();
		XMPPServer.getInstance().getIQRouter().addHandler(olMeetIQHandler);
		XMPPServer.getInstance().getIQDiscoInfoHandler().addServerFeature("urn:xmpp:http:online-meetings:initiate:0");				
		XMPPServer.getInstance().getIQDiscoInfoHandler().addServerFeature("urn:xmpp:http:online-meetings#galene");		
		
        Log.info("gaelene initiated");
    }

    public static String getPort() {
        return "6060";
    }

    public static String getPortRangeMin() {
        return "10000";
    }

    public static String getPortRangeMax() {
        return "20000";
    }

    public static String getPortMux() {
        return "10000";
    }
	
    public static String getTurnPort() {
        return "10014";
    }

    public String getHome() {
        return galeneHomePath;
    }

    public static String getUrl() {
        return "https://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + JiveGlobals.getProperty("httpbind.port.secure", "7443");
    }

    public static String getIpAddress() {
        String ourHostname = XMPPServer.getInstance().getServerInfo().getHostname();
        String ourIpAddress = "127.0.0.1";

        try {
            ourIpAddress = InetAddress.getByName(ourHostname).getHostAddress();
			
			if (ourIpAddress != null && ourIpAddress.contains(":") && !ourIpAddress.startsWith("[")) {
				ourIpAddress = "[" + ourIpAddress + "]";
			}			
        } catch (Exception e) {

        }

        return ourIpAddress;
    }
	
    public URL getWebappURL() {
        final String override = JiveGlobals.getProperty( "galene.webapp.url.override" );
        if ( override != null && !override.trim().isEmpty() )
        {
            try
            {
                return new URL( override );
            }
            catch ( MalformedURLException e )
            {
                Log.warn( "An override for the webapp address is defined in 'galene.webapp.url.override', but its value is not a valid URL.", e );
            }
        }
        try
        {
            final String protocol = "https"; // No point in providing the non-SSL protocol, as webRTC won't work there.
            final String host = XMPPServer.getInstance().getServerInfo().getHostname();
            final int port = Integer.parseInt(JiveGlobals.getProperty("httpbind.port.secure", "7443"));
            final String path = "/group/public";

            return new URL( protocol, host, port, path );
        }
        catch ( MalformedURLException e )
        {
            Log.error( "Unable to compose the webapp URL", e );
            return null;
        }
    }	

    public void onOutputLine(final String line) {
        Log.debug("onOutputLine " + line);
    }

    public void onProcessQuit(int code) {
        Log.debug("onProcessQuit " + code);
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
        jspService = new WebAppContext(null, pluginDirectory.getPath() + "/classes/jsp",  "/galene");
        jspService.setClassLoader(this.getClass().getClassLoader());
        jspService.getMimeTypes().addMimeMapping("wasm", "application/wasm");

        Log.debug("Galene jsp service enabled");
        HttpBindManager.getInstance().addJettyHandler(jspService);
    }

    private void startGoProcesses(File pluginDirectory)
    {
        boolean galeneEnabled = JiveGlobals.getBooleanProperty("galene.enabled", true);

        if (galeneExePath != null && galeneEnabled)	{
            createAdminUser();
            setupGaleneFiles();
			
			galeneWsContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
			galeneWsContext.setContextPath("/galene-ws");

			JettyWebSocketServletContainerInitializer.configure(galeneWsContext, (servletContext, wsContainer) ->
			{
				wsContainer.setMaxTextMessageSize(65535);
				wsContainer.addMapping("/*", new GaleneSocketCreator());
			});	

			HttpBindManager.getInstance().addJettyHandler(galeneWsContext);				
			
			String updMin = JiveGlobals.getProperty("galene.port.range.min", getPortRangeMin());
			String updMax = JiveGlobals.getProperty("galene.port.range.max", getPortRangeMax());
			String updMux = JiveGlobals.getProperty("galene.port.mux", getPortMux());
			
			boolean turnEnabled = JiveGlobals.getBooleanProperty("galene.turn.enabled", false);
            String turn = JiveGlobals.getProperty("galene.turn.ipaddr", getIpAddress()) + ":" + JiveGlobals.getProperty("galene.turn.port", "3478");
			String portValue = JiveGlobals.getBooleanProperty("galene.mux.enabled", false) ? updMux : (updMin + "-" + updMax);
            String params = "--insecure=true --http=:" + getPort() + (turnEnabled ? " --turn=" + turn : "")  + " --udp-range=" + portValue;
            
			galeneThread = Spawn.startProcess(galeneExePath + " " + params, new File(galeneHomePath), this);
			
            galeneContext = new ServletContextHandler(null, "/", ServletContextHandler.SESSIONS);
            galeneContext.setClassLoader(this.getClass().getClassLoader());			
            ServletHolder proxyServlet = new ServletHolder(ProxyServlet.Transparent.class);
            String galeneUrl = "http://" + JiveGlobals.getProperty("galene.ipaddr", getIpAddress()) + ":" + JiveGlobals.getProperty("galene.port", getPort());
            proxyServlet.setInitParameter("proxyTo", galeneUrl);
            proxyServlet.setInitParameter("prefix", "/");
            galeneContext.addServlet(proxyServlet, "/*");
            HttpBindManager.getInstance().addJettyHandler(galeneContext);			
			
			try
			{	
				Thread.sleep(1000);
				startAdminConnection();
			}
			catch (Exception e)
			{
				Log.error("startGoProcesses error", e);
			}		

            Log.info("Galene enabled " + galeneExePath + " " + params);

        } else {
            Log.info("Galene disabled");
        }
    }
	
    public static class GaleneSocketCreator implements JettyWebSocketCreator
    {
        @Override public Object createWebSocket(JettyServerUpgradeRequest req, JettyServerUpgradeResponse resp)
        {			
			String ipaddr = JiveGlobals.getProperty("galene.ipaddr", getIpAddress());
			String port = JiveGlobals.getProperty("galene.port", getPort());

            HttpServletRequest request = req.getHttpServletRequest();
            String path = request.getRequestURI();
            String query = request.getQueryString();
            List<String> protocols = new ArrayList<String>();

            for (String subprotocol : req.getSubProtocols())
            {
                Log.debug("WSocketCreator found protocol " + subprotocol);
                resp.setAcceptedSubProtocol(subprotocol);
                protocols.add(subprotocol);
            }

            if (query != null) path += "?" + query;

            Log.debug("GaleneSocketCreator " + path + " " + query);
            String url = "ws://" + ipaddr + ":" + port + "/ws";

            ProxyWebSocket socket = null;
            ProxyConnection proxyConnection = new ProxyConnection(URI.create(url), protocols, 10000, null);

            socket = new ProxyWebSocket();
            socket.setProxyConnection(proxyConnection);
            return socket;
        }
    }	

    private void checkNatives(File pluginDirectory)
    {
        try
        {
            galeneRoot = JiveGlobals.getHomePath().resolve("galene");

            if (!Files.exists(galeneRoot))
            {
                Files.createDirectories(galeneRoot);
            }

            galeneHomePath = pluginDirectory.getAbsolutePath() + File.separator + "classes";

            if(OSUtils.IS_LINUX64)
            {
                galeneHomePath = galeneHomePath + File.separator + "linux-64";
                galeneExePath = galeneHomePath + File.separator + "galene";
                makeFileExecutable(galeneExePath);
            }
            else 
				
			if(OSUtils.IS_MAC64) {
                galeneHomePath = galeneHomePath + File.separator + "macos-64";
                galeneExePath = galeneHomePath + File.separator + "galene";
                makeFileExecutable(galeneExePath);				

            }
            else 
				
			if(OSUtils.IS_WINDOWS64) {
                galeneHomePath = galeneHomePath + File.separator + "win-64";
                galeneExePath = galeneHomePath + File.separator + "galene.exe";
                makeFileExecutable(galeneExePath);				

            } 
			
			else {
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
        Log.debug("checkNatives galene executable path " + path);
    }

    public void setupGaleneFiles()
    {
        String iniFileName = galeneHomePath + "/data/config.json";
        List<String> lines = new ArrayList<String>();
        Log.debug("Creating config " + iniFileName);

		/*
		{
			"users":{"root": {"password":"secret", "permissions": "admin"}}
		}		
		*/
		
        JSONObject json = new JSONObject();			
        JSONObject users = new JSONObject();	
        JSONObject admin = new JSONObject();
		
        admin.put("permissions", "admin");
		admin.put("password", JiveGlobals.getProperty("galene.password", "sfu-admin"));	

		users.put(JiveGlobals.getProperty("galene.username", "sfu-admin"), admin);
		json.put("users", users);
		json.put("writableGroups", true);
		
		String hostname = XMPPServer.getInstance().getServerInfo().getHostname();
		JSONArray origins = new JSONArray();
		origins.put(0, hostname);
		json.put("allowOrigin", origins);
		json.put("allowAdminOrigin", origins);
		
		//json.put("proxyURL",  JiveGlobals.getProperty("galene.url", "http://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + getPort()));		
		
		lines.add(json.toString());

        try {
			File fil = new File(iniFileName);
			fil.setReadable(true, true);
			fil.setWritable(true, true);	
			
            Path file = Paths.get(iniFileName);
            Files.write(file, lines, Charset.forName("UTF-8"));
        }
        catch ( Exception e ) {
            Log.error( "Unable to write file " + iniFileName, e );
        }

		if (JiveGlobals.getBooleanProperty("galene.muc.enabled", false)) {
			String service = "conference";
			List<MUCRoom> rooms = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(service).getActiveChatRooms();

			for (MUCRoom room : rooms) {					
				writeGaleneGroupFile(room.getJID(), null);
			}	
		}

        String publicGroupFileName = galeneHomePath + "/groups/public.json";
        lines = new ArrayList<String>();
        Log.debug("Creating public group " + publicGroupFileName);
		lines.add("{");
		lines.add("  \"public\": true,");
		lines.add("  \"description\": \"A public place to hangout\",");

		JSONArray authKeys = new JSONArray();	
		JSONObject authKey = new JSONObject();
		
		authKey.put("kty", "oct");
		authKey.put("alg", "HS256");
		authKey.put("k", JWebToken.SECRET_KEY);
		authKey.put("kid", "0");		
		authKeys.put(0, authKey);
				
		lines.add("  \"authKeys\": " + authKeys + ",");				
		lines.add("  \"authServer\": \"https://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + JiveGlobals.getProperty("httpbind.port.secure", "7443") + "/galene/auth-server\", ");								
		
  		lines.add("  \"allow-recording\": true,");
  		lines.add("  \"allow-subgroups\": true,");
  		lines.add("  \"op\": [{}],");
  		lines.add("  \"presenter\": [{}],");
  		lines.add("  \"other\": [{}],  ");
  		lines.add("  \"codecs\": [\"vp9\", \"opus\", \"av1\", \"h264\"],   ");
  		lines.add("  \"max-clients\": 100");
		lines.add("}");

        try  {
			File fil = new File(publicGroupFileName);
			fil.setReadable(true, true);
			fil.setWritable(true, true);	
			
            Path file = Paths.get(publicGroupFileName);
            Files.write(file, lines, Charset.forName("UTF-8"));
        }
        catch ( Exception e ) {
            Log.error( "Unable to write file " + publicGroupFileName, e );
        }		
    }

    private void createAdminUser()
    {
        final UserManager userManager = XMPPServer.getInstance().getUserManager();
        final String administrator = JiveGlobals.getProperty("galene.username", "sfu-admin");

        if ( !userManager.isRegisteredUser( new JID(administrator + "@" + XMPPServer.getInstance().getServerInfo().getXMPPDomain()), false ) )
        {
            Log.debug( "No administrator user detected. Generating one." );

            try
            {
                String password = StringUtils.randomString(40);
                JiveGlobals.setProperty("galene.password", password);
                userManager.createUser(administrator, password, "SFU Administrator (generated)", null);
            }
            catch ( Exception e )
            {
                Log.error( "Unable to provision an administrator user.", e );
            }
        }
    }

    // -------------------------------------------------------
    //
    //  MUCEventListener
    //
    // -------------------------------------------------------

    public void roomCreated(JID roomJID)
    {
		if (JiveGlobals.getBooleanProperty("galene.muc.enabled", false)) {		
			MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomJID.getNode());

			if (room != null && room.isPersistent()) {
				writeGaleneGroupFile(room.getJID(), null);
			}
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

    public void messageReceived(JID roomJID, JID user, String nickname, Message message)  {
		final String from = user.toBareJID();
		final String room = roomJID.getNode();	
		final String body = message.getBody();
	
		if (body != null && !GaleneIQHandler.connections.containsKey(from)) {
			Log.debug("messageReceived " + from + " " + room + "\n" + body);
			
			for (ProxyConnection conn : GaleneIQHandler.connections.values()) 
			{
				if (room.equals(conn.room)) {
					JSONObject json = new JSONObject();
					json.put("type", "chat");
					json.put("username", nickname);			
					json.put("source", "");			
					json.put("value", body);
					json.put("time", System.currentTimeMillis());							
					conn.onMessage(json.toString());
				}					
			}	
		}			
    }

    public void roomSubjectChanged(JID roomJID, JID user, String newSubject)
    {

    }

    public void privateMessageRecieved(JID a, JID b, Message message)
    {

    }
	
    public void roomClearChatHistory(long roomID, JID roomJID) {

    }

    public void roomCreated(long roomID, JID roomJID) {

    }
	
    public void roomDestroyed(long roomID, JID roomJID) {

    }	
	
	public Map<String, String> getGroupChatProperties(JID roomJID) {
		Map<String, String> properties = null;
		
		if (JiveGlobals.getBooleanProperty("galene.enabled", false)) {
			properties = (Map<String, String>) muc_properties.get(roomJID.toString());
			
			if (properties == null) {
				MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomJID.getNode());			
				
				if (room != null) {
					properties = new MUCRoomProperties(room.getID());
					muc_properties.put(room.getJID().toString(), properties);
				}
			}

			if (!properties.containsKey("galene.enabled")) properties.put("galene.enabled", "true");
			if (!properties.containsKey("galene.federation.enabled")) properties.put("galene.federation.enabled", "false");		
			
			// Dont create default passwords
			//if (!properties.containsKey("galene.owner.password")) properties.put("galene.owner.password", StringUtils.randomString(40));
			//if (!properties.containsKey("galene.admin.password")) properties.put("galene.admin.password", StringUtils.randomString(40));
		}
		
		return properties;
	}

    public void writeGaleneGroupFile(JID roomJID, Map<String, String> properties)
    {
		MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(roomJID).getChatRoom(roomJID.getNode());					
        Log.debug("writeGaleneGroupFile " + roomJID + " " + room);

		if (room == null) return;

        JSONObject json = new JSONObject();
		
        String roomName = room.getJID().getNode();
        String iniFileName = galeneHomePath + "/groups/" + roomName + ".json";		
        String password = room.getPassword();
		
		if (properties == null) properties = (Map<String, String>) muc_properties.get(room.getJID().toString());		
		if (properties == null) properties = getGroupChatProperties(roomJID);
		
		(new File(iniFileName)).delete();
		
		if ("true".equals(properties.get("galene.enabled"))) {	
			JSONArray op = new JSONArray();
			JSONArray presenter = new JSONArray();
			JSONArray other = new JSONArray();

			String pass = properties.get("galene.owner.password");
			
			if (pass != null) {		
				JSONObject owner = new JSONObject();								
				owner.put("password", pass);
				op.put(0, owner);						
			}			

			json.put("op", op);
			pass = properties.get("galene.admin.password");			
			
			if (pass != null) {		
				JSONObject admin = new JSONObject();					
				admin.put("password", pass);
				presenter.put(0, admin);					
			}				
			json.put("presenter", presenter);

			if (password != null && !password.isEmpty()) {
				JSONObject member = new JSONObject();				
				member.put("password", password);
				other.put(0, member);					
			} 
			else  {
				other.put(0, new JSONObject());  // anybody is member
			}			
			json.put("other", other);
			
			JSONArray authKeys = new JSONArray();	
			JSONObject authKey = new JSONObject();
			
			authKey.put("kty", "oct");
			authKey.put("alg", "HS256");
			authKey.put("k", JWebToken.SECRET_KEY);
			authKey.put("kid", "0");		
			authKeys.put(0, authKey);
			
			json.put("authKeys", authKeys);				
			json.put("authServer", "https://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + JiveGlobals.getProperty("httpbind.port.secure", "7443") + "/galene/auth-server");								

			json.put("public", room.isPublicRoom());
			json.put("description", room.getDescription());
			json.put("contact", room.getName());
			json.put("comment", room.getSubject());
			json.put("allow-recording", room.isLogEnabled());
			json.put("allow-subgroups", room.canOccupantsInvite());
			json.put("unrestricted-tokens", room.canOccupantsInvite());
			json.put("max-clients", room.getMaxUsers());
			
			JSONArray codecs = new JSONArray();
			codecs.put(0, "vp9");
			codecs.put(1, "av1");		
			codecs.put(2, "opus");			
			codecs.put(3, "h264");	
			json.put("codecs", codecs);			
			
			List<String> lines = new ArrayList<String>();
			lines.add(json.toString());

			Log.debug("Creating " + iniFileName);

			try
			{
				File fil = new File(iniFileName);
				fil.setReadable(true, true);
				fil.setWritable(true, true);	
				
				Path file = Paths.get(iniFileName);
				Files.write(file, lines, Charset.forName("UTF-8"));
			}
			catch ( Exception e )
			{
				Log.error( "Unable to write file " + iniFileName, e );
			}
		}			
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

    //-------------------------------------------------------
    //
    //  Admin connection to Galene
    //
    //-------------------------------------------------------	
	
	private void startAdminConnection() {
		String username = JiveGlobals.getProperty("galene.username", "sfu-admin");	
		String galenePort = JiveGlobals.getProperty("galene.port", Galene.self.getPort());	
		String ipaddr = JiveGlobals.getProperty("galene.ipaddr", getIpAddress());
		String url = "ws://" + ipaddr + ":" + galenePort + "/ws";	
		
		adminConnection = new ProxyConnection(URI.create(url), new ArrayList<String>(), 10000, new JID(username + "@" + domain));
		
		JSONObject handshake = new JSONObject();
		handshake.put("id", username);
		handshake.put("version", new JSONArray("[\"2\"]"));
		sendMessage("handshake", handshake, adminConnection);
	}	
	
	public void onMessage(String text) {
		Log.debug("S2Amin \n" + text);
		JSONObject message = new JSONObject(text);

		if (message.has("type")) {
			if ("handshake".equals(message.getString("type"))) {
				Log.debug("Galene Administrator user connected"); 
				if (message.has("id")) adminConnection.id = message.getString("id");
			}
			
			if ("ping".equals(message.getString("type"))) sendMessage("pong", new JSONObject(), adminConnection);			
		}
	}

	private void sendMessage(String type, JSONObject payload, ProxyConnection connection) {
		payload.put("type", type);			
		String text = payload.toString();
		//Log.debug("Admin2S \n" + text);
		connection.deliver(text);
	}

	public void terminateClient(String id, String group) {
		/*
		 {
			 "type":"join",
			 "kind":"join",
			 "group":"public/andrew",
			 "username":"dele",
			 "password":""
		 }
		
		 {
			 "type":"useraction",
			 "source":"7a8864fce6e6537830725be91bc688a7",
			 "dest":"7a8864fce6e6537830725be91bc688a7",
			 "username":"dele",
			 "kind":"kick",
			 "value":""
		}

		 {
			 "type":"join",
			 "kind":"leave",
			 "group":"public/andrew",
			 "username":"dele",
			 "password":""
		 }		
		
		*/ 
		
		if (GaleneIQHandler.clients.containsKey(id)) {	
			ProxyConnection connection	= GaleneIQHandler.clients.get(id);
			
			JSONObject useraction = new JSONObject();
			useraction.put("source", connection.id);
			useraction.put("dest", id);
			useraction.put("username", connection.username);
			useraction.put("kind", "kick");
			useraction.put("value", "");
			sendMessage("useraction", useraction, connection);			
		}
	}
	
    //-------------------------------------------------------
    //
    //  Utility methods
    //
    //-------------------------------------------------------	

	public void intercept(String text, JID from, ProxyConnection connection) {
		JSONObject message = new JSONObject(text);
		/*
		{
			type: 'handshake',
			version: ["2"],
			id: id
		}
		*/
		if (message.has("type") && "handshake".equals(message.getString("type")) && message.has("id")) {
			String id = message.getString("id");	
			connection.id = id;
			GaleneIQHandler.clients.put(id, connection);
		}
		else

		/*
		{
			type: 'join',
			kind: 'join' or 'leave',
			group: group,
			username: username,
			password: password,
			data: data
		}
		*/
		if (message.has("type") && "join".equals(message.getString("type")) && message.has("group")) {
			String group = message.getString("group");	
			
			if (message.has("username")) {
				String username = message.getString("username");				
				connection.username = username;
				
				if (message.has("password")) {
					String password = message.getString("password");				
					connection.password = password;				
				}
			}
			
			if (from != null && !group.startsWith("public")) {
				String room = group.split("/")[0];
				Log.debug("User " + from + " joins " + room);
				connection.room = room;
				
				Presence pres = new Presence();	
				pres.setTo(room + "@conference." + domain + "/" + from.getNode() + " (" + group + ")");	
				pres.setFrom(from);
				pres.addChildElement("x", "http://jabber.org/protocol/muc");						
				
				XMPPServer.getInstance().getPresenceRouter().route(pres);					
			}
		}
		else
			
		/*
		{
			type: 'chat',
			kind: '' or 'me',
			source: source-id,
			username: username,
			dest: dest-id,
			privileged: boolean,
			time: time,
			noecho: false,
			value: message
		}
		*/	
		if (message.has("type") && "chat".equals(message.getString("type")) && message.has("value") && connection.room != null) {
			String source = message.getString("source");
			String dest = message.getString("dest");
			String value = message.getString("value");	
			String service = "conference";

			MultiUserChatService mucService = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(service);
			MUCRoom room = mucService.getChatRoom(connection.room);					
			
			if (room != null) {				
				Message msg = new Message();	
				msg.setBody(value);								
				
				if (dest.isEmpty()) {						
					msg.setType(Message.Type.groupchat);							
					msg.setFrom(connection.jid);							
					msg.setTo(connection.room + "@" + service + "." + domain);		
					
					XMPPServer.getInstance().getMessageRouter().route(msg);						
				}			
			}
		}		
	}
	
	public String getJson(String urlToRead)  {
		URL url;
		HttpURLConnection conn;
		BufferedReader rd;
		String line;
		StringBuilder result = new StringBuilder();

		String username = JiveGlobals.getProperty("galene.username", "sfu-admin");
		String password = JiveGlobals.getProperty("galene.password", "sfu-admin");		
		String auth = username + ":" + password;
		String authHeaderValue = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));	;
		String galenePort = JiveGlobals.getProperty("galene.port", getPort());	
		String ipaddr = JiveGlobals.getProperty("galene.ipaddr", getIpAddress());
		String uri = "http://" + ipaddr + ":" + galenePort + urlToRead;			

		try {
			url = new URL(uri);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestProperty("Authorization", authHeaderValue);			
			conn.setRequestMethod("GET");
			rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			
			while ((line = rd.readLine()) != null) {
				result.append(line);
			}
			rd.close();

		} catch (Exception e) {
			Log.error("getJson", e);
		}

		return result.toString();
	}	
	/*
	[
	  {
		"name": "public"
	  },
	  {
		"name": "public/andrew",
		"clients": [
		  {
			"id": "6ffd7537fbaacfeb71ea541f3e9089bd",
			"up": [
			  {
				"id": "ab1130bc0f24e6bda1a543fbc3a128a7",
				"tracks": [
				  {
					"bitrate": 36576,
					"maxBitrate": 512000,
					"loss": 0,
					"jitter": 0.499992
				  },
				  {
					"bitrate": 653600,
					"maxBitrate": 708004,
					"loss": 0,
					"jitter": 7.188817
				  }
				]
			  }
			],
			"down": [
			  {
				"id": "21c21315456adf851c1518251e20660d",
				"tracks": [
				  {
					"sid": 0,
					"maxSid": 0,
					"tid": 0,
					"maxTid": 0,
					"bitrate": 36616,
					"maxBitrate": 512000,
					"loss": 0,
					"rtt": 0.583577,
					"jitter": 0.291666
				  },
				  {
					"sid": 0,
					"maxSid": 0,
					"tid": 0,
					"maxTid": 0,
					"bitrate": 715008,
					"maxBitrate": 1024327,
					"loss": 0,
					"rtt": 0.838623,
					"jitter": 8.255555
				  }
				]
			  }
			]
		  },
		  {
			"id": "e7be0b2866dbdaa244c9e6c0a38f51d1",
			"up": [
			  {
				"id": "21c21315456adf851c1518251e20660d",
				"tracks": [
				  {
					"bitrate": 37192,
					"maxBitrate": 512000,
					"loss": 0,
					"jitter": 0.395827
				  },
				  {
					"bitrate": 712432,
					"maxBitrate": 1024327,
					"loss": 0,
					"jitter": 5.288836
				  }
				]
			  }
			],
			"down": [
			  {
				"id": "ab1130bc0f24e6bda1a543fbc3a128a7",
				"tracks": [
				  {
					"sid": 0,
					"maxSid": 0,
					"tid": 0,
					"maxTid": 0,
					"bitrate": 37104,
					"maxBitrate": 512000,
					"loss": 0,
					"rtt": 0.868041,
					"jitter": 0.4375
				  },
				  {
					"sid": 0,
					"maxSid": 0,
					"tid": 0,
					"maxTid": 0,
					"bitrate": 630360,
					"maxBitrate": 708004,
					"loss": 0,
					"rtt": 0.661824,
					"jitter": 10.388888
				  }
				]
			  }
			]
		  }
		]
	  }
	]	
	*/
}
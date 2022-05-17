package org.ifsoft.galene.openfire;

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

public class Galene implements Plugin, PropertyEventListener, ProcessListener
{
    private static final Logger Log = LoggerFactory.getLogger(Galene.class);
    private XProcess galeneThread = null;
    private String galeneExePath = null;
    private String galeneHomePath = null;
    private String galeneRoot = null;
    private ExecutorService executor;
    private WebAppContext jspService;
    private GaleneIQHandler galeneIQHandler;
    private RayoIQHandler rayoIQHandler;
	
    public static Galene self;	

    public void destroyPlugin()
    {
        PropertyEventDispatcher.removeListener(this);

        try {
            if (executor != null)  executor.shutdown();
            if (galeneThread != null) galeneThread.destory();
            if (jspService != null) HttpBindManager.getInstance().removeJettyHandler(jspService);
		
            XMPPServer.getInstance().getIQRouter().removeHandler(galeneIQHandler);
			galeneIQHandler.stopHandler();
            galeneIQHandler = null;		

			rayoIQHandler.stopHandler();
            rayoIQHandler = null;				

            Log.info("gaelene terminated");
        }
        catch (Exception e) {
            Log.error("Galene destroyPlugin", e);
        }
    }

    public void initializePlugin(final PluginManager manager, final File pluginDirectory)
    {		
        PropertyEventDispatcher.addListener(this);
        checkNatives(pluginDirectory);
        executor = Executors.newCachedThreadPool();
        startJSP(pluginDirectory);
        startGoProcesses(pluginDirectory);
        self = this;	
		
		galeneIQHandler = new GaleneIQHandler();
		galeneIQHandler.startHandler();		
		XMPPServer.getInstance().getIQRouter().addHandler(galeneIQHandler);	
		XMPPServer.getInstance().getIQDiscoInfoHandler().addServerFeature("urn:xmpp:sfu:galene:0");			
		
		rayoIQHandler = new RayoIQHandler();
		rayoIQHandler.startHandler();				
		
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
        return galeneHomePath;
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
        jspService = new WebAppContext(null, pluginDirectory.getPath() + "/classes/jsp",  "/galene");
        jspService.setClassLoader(this.getClass().getClassLoader());
        jspService.getMimeTypes().addMimeMapping("wasm", "application/wasm");

        final List<ContainerInitializer> initializers = new ArrayList<>();
        initializers.add(new ContainerInitializer(new JettyJasperInitializer(), null));
        jspService.setAttribute("org.eclipse.jetty.containerInitializers", initializers);
        jspService.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());

        Log.info("Galene jsp service enabled");
        HttpBindManager.getInstance().addJettyHandler(jspService);
    }

    private void startGoProcesses(File pluginDirectory)
    {
        boolean galeneEnabled = JiveGlobals.getBooleanProperty("galene.enabled", true);

        if (galeneExePath != null && galeneEnabled)
        {
            createAdminUser();
            setupGaleneFiles();

            String turn = JiveGlobals.getProperty("galene.turn.ipaddr", getIpAddress()) + ":" + JiveGlobals.getProperty("galene.turn.port", "10014");
            String params = "--insecure=true --http=:" + getPort() + " --turn=" + turn;
            galeneThread = Spawn.startProcess(galeneExePath + " " + params, new File(galeneHomePath), this);

            Log.info("Galene enabled " + galeneExePath);

        } else {
            Log.info("Galene disabled");
        }
    }

    private void checkNatives(File pluginDirectory)
    {
        try
        {
            galeneRoot = JiveGlobals.getHomeDirectory() + File.separator + "galene";

            File galeneRootPath = new File(galeneRoot);

            if (!galeneRootPath.exists())
            {
                galeneRootPath.mkdirs();
            }

            galeneHomePath = pluginDirectory.getAbsolutePath() + File.separator + "classes";

            if(OSUtils.IS_LINUX64)
            {
                galeneHomePath = galeneHomePath + File.separator + "linux-64";
                galeneExePath = galeneHomePath + File.separator + "galene";
                makeFileExecutable(galeneExePath);
            }
            else if(OSUtils.IS_WINDOWS64)
            {
                galeneHomePath = galeneHomePath + File.separator + "win-64";
                galeneExePath = galeneHomePath + File.separator + "galene.exe";
                makeFileExecutable(galeneExePath);				
            }
            else if(OSUtils.IS_WINDOWS32)
            {
                galeneHomePath = galeneHomePath + File.separator + "win-32";
                galeneExePath = galeneHomePath + File.separator + "galene.exe";
                makeFileExecutable(galeneExePath);

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

    private void setupGaleneFiles()
    {
        String iniFileName = galeneHomePath + "/data/config.json";
        List<String> lines = new ArrayList<String>();
        Log.info("Creating " + iniFileName);
		
        // create galene global config.json file

        JSONObject json = new JSONObject();	
		
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
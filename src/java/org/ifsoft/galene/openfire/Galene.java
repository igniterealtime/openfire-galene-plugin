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
        Log.info(line);
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
	
    //-------------------------------------------------------
    //
    //  Utility methods
    //
    //-------------------------------------------------------	

	public String getJson(String urlToRead)  {
		URL url;
		HttpURLConnection conn;
		BufferedReader rd;
		String line;
		StringBuilder result = new StringBuilder();

		String username = JiveGlobals.getProperty("galene.username", "administrator");
		String password = JiveGlobals.getProperty("galene.password", "administrator");		
		String auth = username + ":" + password;
		String authHeaderValue = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));	;
		String uri = "http://localhost:" + getPort() + urlToRead;

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

	// [{"name":"public"}]	
	// [{"name":"public"},{"name":"public/andrew"}]
	// [{"name":"public"},{"name":"public/andrew","clients":[{"id":"e7be0b2866dbdaa244c9e6c0a38f51d1"}]}]
	// [{"name":"public"},{"name":"public/andrew","clients":[{"id":"e7be0b2866dbdaa244c9e6c0a38f51d1","up":[{"id":"ecf670561db25979306de18e301f4df4","tracks":[{"bitrate":0,"maxBitrate":204800,"loss":0,"jitter":0.333328},{"bitrate":0,"maxBitrate":204800,"loss":0,"jitter":3.655519}]}]}]}]
	// [{"name":"public"},{"name":"public/andrew","clients":[{"id":"e7be0b2866dbdaa244c9e6c0a38f51d1","up":[{"id":"ecf670561db25979306de18e301f4df4","tracks":[{"bitrate":0,"maxBitrate":512000,"loss":0,"jitter":0.499992},{"bitrate":0,"maxBitrate":512000,"loss":0,"jitter":7.344371}]}]},{"id":"e8a9f45f4be9c58704c1536384d2c270","down":[{"id":"ecf670561db25979306de18e301f4df4","tracks":[{"sid":0,"maxSid":0,"tid":0,"maxTid":0,"bitrate":0,"maxBitrate":512000,"loss":0,"jitter":0.708333},{"sid":0,"maxSid":0,"tid":0,"maxTid":0,"bitrate":0,"maxBitrate":512000,"loss":0,"jitter":7.966666}]}]}]}]
	// [{"name":"public"},{"name":"public/andrew","clients":[{"id":"6ffd7537fbaacfeb71ea541f3e9089bd","up":[{"id":"ab1130bc0f24e6bda1a543fbc3a128a7","tracks":[{"bitrate":36576,"maxBitrate":512000,"loss":0,"jitter":0.499992},{"bitrate":653600,"maxBitrate":708004,"loss":0,"jitter":7.188817}]}],"down":[{"id":"21c21315456adf851c1518251e20660d","tracks":[{"sid":0,"maxSid":0,"tid":0,"maxTid":0,"bitrate":36616,"maxBitrate":512000,"loss":0,"rtt":0.583577,"jitter":0.291666},{"sid":0,"maxSid":0,"tid":0,"maxTid":0,"bitrate":715008,"maxBitrate":1024327,"loss":0,"rtt":0.838623,"jitter":8.255555}]}]},{"id":"e7be0b2866dbdaa244c9e6c0a38f51d1","up":[{"id":"21c21315456adf851c1518251e20660d","tracks":[{"bitrate":37192,"maxBitrate":512000,"loss":0,"jitter":0.395827},{"bitrate":712432,"maxBitrate":1024327,"loss":0,"jitter":5.288836}]}],"down":[{"id":"ab1130bc0f24e6bda1a543fbc3a128a7","tracks":[{"sid":0,"maxSid":0,"tid":0,"maxTid":0,"bitrate":37104,"maxBitrate":512000,"loss":0,"rtt":0.868041,"jitter":0.4375},{"sid":0,"maxSid":0,"tid":0,"maxTid":0,"bitrate":630360,"maxBitrate":708004,"loss":0,"rtt":0.661824,"jitter":10.388888}]}]}]}]
	
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
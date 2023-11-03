package org.ifsoft.websockets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import javax.xml.bind.DatatypeConverter;
import java.time.Duration;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import javax.net.*;
import javax.net.ssl.*;
import javax.security.auth.callback.*;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.XMPPServer;

import net.sf.json.*;
import org.dom4j.Element;
import org.xmpp.packet.*;
import org.ifsoft.galene.openfire.Galene;


public class GaleneConnection implements Serializable {
    private static Logger Log = LoggerFactory.getLogger( "GaleneConnection" );
    private boolean connected = false;
    private WebSocketClient wsClient = null;
    private ProxySocket proxySocket = null;

	public JID jid;
	public String room;		
	public String id;

    public GaleneConnection(URI uri, int connectTimeout, JID jid) {
        Log.debug("GaleneConnection " + uri + " " + jid);
		this.jid = jid;
		
        final HttpClient httpClient = new HttpClient();
        final QueuedThreadPool queuedThreadPool = QueuedThreadPoolProvider.getQueuedThreadPool("GaleneConnection-HttpClient");
        httpClient.setExecutor(queuedThreadPool);
        httpClient.setConnectTimeout(connectTimeout);
        wsClient = new WebSocketClient();
		wsClient.setIdleTimeout(Duration.ofMinutes(5));

        try {
            wsClient.start();
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            proxySocket = new ProxySocket(this);
            wsClient.connect(proxySocket, uri, request);

            Log.debug("Connecting to : " + uri);
            connected = true;
        }
        catch (Exception e) {
            Log.error("GaleneConnection " + uri, e);
            connected = false;
        }
    }

	public String getFullId() {
		return jid != null ? jid.toBareJID() : "";
	}
	
	public String getId() {
		return jid != null ? jid.getNode() : "";
	}
	
    public void deliver(String text) {
        Log.debug("GaleneConnection - deliver \n" + text);

        if (proxySocket != null) {
            proxySocket.deliver(text);
        }
    }

    public void stop() {
        Log.debug("GaleneConnection - stop");

        try {
            wsClient.stop();
        }
        catch (Exception e) {
            Log.error("GaleneConnection - stop", e);
        }
    }

    public void disconnect() {
        Log.debug("GaleneConnection - disconnect");
        if (proxySocket != null) proxySocket.disconnect();
        if (wsClient != null) stop();
    }

    public void onClose(int code, String reason) {
        Log.debug("GaleneConnection - onClose " + reason + " " + code);
        connected = false;
    }

    public void onMessage(String text) {
        Log.debug("S2C \n" + text);

		if (jid != null) {
			try {
				IQ iq = new IQ(IQ.Type.set);
				iq.setTo(jid);
				iq.setType(IQ.Type.set);
				iq.setFrom(XMPPServer.getInstance().getServerInfo().getHostname());
				Element galene = iq.setChildElement("s2c", "urn:xmpp:sfu:galene:0");
				Element json = galene.addElement("json", "urn:xmpp:json:0");
				json.setText(text);
				XMPPServer.getInstance().getIQRouter().route(iq);	
			}
			catch (Exception e) {
				Log.error("deliverRawText error", e);
			}
		} else {
			Galene.self.onMessage(text);
		}
    }
	
    public boolean isConnected() {
        return connected;
    }
	
	@WebSocket(maxTextMessageSize = 64 * 1024) public class ProxySocket
    {
        private Session session;
        private GaleneConnection proxyConnection;
        private String ipaddr = null;

        public ProxySocket(GaleneConnection proxyConnection)
        {
            this.proxyConnection = proxyConnection;
        }		

        @OnWebSocketError public void onError(Throwable t)
        {
            Log.error("Error: "  + t.getMessage(), t);
        }

        @OnWebSocketClose public void onClose(int statusCode, String reason)
        {
            Log.debug("ProxySocket onClose " + statusCode + " " + reason);
            this.session = null;
            if (proxyConnection != null) proxyConnection.onClose(statusCode, reason);
        }

        @OnWebSocketConnect public void onConnect(Session session)
        {
            Log.debug("ProxySocket onConnect: " + session);
            this.session = session;
        }

        @OnWebSocketMessage public void onMessage(String msg)
        {
            Log.debug("ProxySocket onMessage \n" + msg);
			
            if (proxyConnection != null) {
				proxyConnection.onMessage(msg);	
			}				
        }

        public void deliver(String text)
        {
            try {
                Log.debug("ProxySocket deliver: \n" + text);

                while (session == null) Thread.sleep(1000);

                session.getRemote().sendString(text);
            } catch (Exception e) {
                Log.error("ProxySocket deliver", e);
            }
        }

        public void disconnect()
        {
            if (session != null) session.close(StatusCode.NORMAL,"I'm done");
        }

    }
}
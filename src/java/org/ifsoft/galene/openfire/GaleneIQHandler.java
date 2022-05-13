package org.ifsoft.galene.openfire;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.openfire.event.SessionEventListener;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import org.ifsoft.websockets.*;
import net.sf.json.*;

/**
 * custom IQ handler for Galene SFU requests and responses
 */
public class GaleneIQHandler extends IQHandler implements SessionEventListener
{
    private final static Logger Log = LoggerFactory.getLogger( GaleneIQHandler.class );	
    //private static Cache<String, GaleneConnection> connections = CacheFactory.createLocalCache("SFU Connections");	
	private ConcurrentHashMap<String, GaleneConnection> connections = new ConcurrentHashMap<>();

	public void startHandler() {
		SessionEventDispatcher.addListener(this);
	}

	public void stopHandler() {
		SessionEventDispatcher.removeListener(this);	
	}
	
    public GaleneIQHandler( )
    {
        super("Galene IQ Handler");
    }

    @Override
    public IQ handleIQ(IQ iq)
    {
		if (iq.getType() == IQ.Type.set || iq.getType() == IQ.Type.get) {
			IQ reply = IQ.createResultIQ(iq);

			try {
				Log.info("Galene handleIQ \n" + iq.toString());
				final Element element = iq.getChildElement();
				final String from = iq.getFrom().toBareJID();
				GaleneConnection connection = (GaleneConnection) connections.get(from);
				
				if (connection == null || !connection.isConnected()) {
					String galenePort = JiveGlobals.getProperty("galene.port", Galene.self.getPort());		
					String url = "ws://localhost:" + galenePort + "/ws";		
					connection = new GaleneConnection(URI.create(url), 10000, iq.getFrom());	
					
					try {
						connections.put(from, connection);
					} catch (Exception e) {
						Log.error("Galene handleIQ Cache error", e);						
					}
				}

				connection.deliver(element.getText());
				return reply;

			} catch(Exception e) {
				Log.error("Galene handleIQ", e);
				reply.setError(new PacketError(PacketError.Condition.internal_server_error, PacketError.Type.modify, e.toString()));
				return reply;
			}
		}
		return null;
    }	

    @Override
    public IQHandlerInfo getInfo()
    {
        return new IQHandlerInfo("c2s", "urn:xmpp:sfu:galene:0");
    }

    private String removeNull(String s)
    {
        if (s == null)
        {
            return "";
        }

        return s.trim();
    }	
	
    //-------------------------------------------------------
    //
    //      session management
    //
    //-------------------------------------------------------

    public void anonymousSessionCreated(Session session)
    {
        Log.debug("GaleneIQHandler -  anonymousSessionCreated "+ session.getAddress().toString() + "\n" + ((ClientSession) session).getPresence().toXML());
    }

    public void anonymousSessionDestroyed(Session session)
    {
        Log.debug("GaleneIQHandler -  anonymousSessionDestroyed "+ session.getAddress().toString() + "\n" + ((ClientSession) session).getPresence().toXML());
    }

    public void resourceBound(Session session)
    {
        Log.debug("GaleneIQHandler -  resourceBound "+ session.getAddress().toString() + "\n" + ((ClientSession) session).getPresence().toXML());
    }

    public void sessionCreated(Session session)
    {
        Log.debug("GaleneIQHandler -  sessionCreated "+ session.getAddress().toString() + "\n" + ((ClientSession) session).getPresence().toXML());
    }

    public void sessionDestroyed(Session session)
    {
        Log.debug("GaleneIQHandler -  sessionDestroyed "+ session.getAddress().toString() + "\n" + ((ClientSession) session).getPresence().toXML());
		
		final String from = session.getAddress().toBareJID();
		GaleneConnection connection = (GaleneConnection) connections.remove(from);	
		if (connection != null) connection.disconnect();
    }	
}

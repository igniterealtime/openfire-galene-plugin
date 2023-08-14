package org.ifsoft.galene.openfire;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.openfire.event.SessionEventListener;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.openfire.muc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.Presence;
import org.xmpp.packet.Message;
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
public class GaleneIQHandler extends IQHandler implements SessionEventListener, ServerFeaturesProvider
{
    private final static Logger Log = LoggerFactory.getLogger( GaleneIQHandler.class );	
	public final static ConcurrentHashMap<String, GaleneConnection> connections = new ConcurrentHashMap<>();
	public final static ConcurrentHashMap<String, GaleneConnection> clients = new ConcurrentHashMap<>();
	private final static String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
	
	
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
				Log.debug("Galene handleIQ \n" + iq.toString());
				final Element element = iq.getChildElement().element("json");
				final String from = iq.getFrom().toBareJID();
				GaleneConnection connection = (GaleneConnection) connections.get(from);
					
				if (element != null) {
					
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

					String text = element.getText();
					Log.debug("C2S \n" + text);
					intercept(text, iq.getFrom(), connection);
					connection.deliver(text);
				}
				else {
					if (connection != null && connection.isConnected()) {
						connection.disconnect();
					}
				}
				return reply;

			} catch(Exception e) {
				Log.error("Galene handleIQ", e);
				reply.setError(new PacketError(PacketError.Condition.internal_server_error, PacketError.Type.modify, e.toString()));
				return reply;
			}
		}
		return null;
    }	
	
	
	private void intercept(String text, JID from, GaleneConnection connection) {
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
			
			if (!group.startsWith("public")) {
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

    @Override
    public IQHandlerInfo getInfo()
    {
        return new IQHandlerInfo("c2s", "urn:xmpp:sfu:galene:0");
    }
	
    @Override
    public Iterator<String> getFeatures()
    {
        final ArrayList<String> features = new ArrayList<>();
        features.add( "urn:xmpp:sfu:galene:0" );
        return features.iterator();
    }	

    private String removeNull(String s)
    {
        if (s == null) {
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
		
		if (connection != null) {
			clients.remove(connection.id);
			connection.disconnect();
		}
    }	
}

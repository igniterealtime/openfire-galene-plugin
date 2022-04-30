package org.ifsoft.websockets;

import java.util.*;

import org.dom4j.Namespace;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.nio.OfflinePacketDeliverer;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.openfire.auth.AuthToken;
import org.xmpp.packet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dom4j.*;
import net.sf.json.*;


public class XmppConnection extends VirtualConnection
{
    private static Logger Log = LoggerFactory.getLogger( "XmppConnection" );	
	private ProxyWebSocket socket;
    private String remoteAddr;	
    private SessionPacketRouter router;	
    private PacketDeliverer backupDeliverer;
    private ConnectionConfiguration configuration;	
    private ConnectionType connectionType;	
	private String username;
	private AuthToken authToken = null;
    private LocalClientSession session;	

    public XmppConnection(ProxyWebSocket socket, String username) {
        this.socket = socket;
		this.username = username;
		
        session = SessionManager.getInstance().createClientSession( this, (Locale) null );			
		session.setAnonymousAuth();	
		router = new SessionPacketRouter( session );		
    }
	
	public void route(String xml)
	{
		try {
			router.route(DocumentHelper.parseText(xml).getRootElement());	
		} catch (Exception e) {
			Log.error("xmpp routing failed", e);
		}			
	}
	
	public JID getJid()
	{
		return session.getAddress();
	}
	
    @Override
    public void closeVirtualConnection()
    {

    }

    @Override
    public byte[] getAddress() {
		return remoteAddr.getBytes();
    }

    @Override
    public String getHostAddress() {
		return remoteAddr;
    }

    @Override
    public String getHostName()  {
        return socket.username;
    }

    @Override
    public void systemShutdown() {

    }

    @Override
    public void deliver(Packet packet) throws UnauthorizedException
    {		
		String room = packet.getFrom().getNode();
		String participant = packet.getFrom().getResource();	
		
        if (packet instanceof Message) 
		{		
			Message message = (Message) packet;
			
			if (message.getType() == Message.Type.groupchat)
			{
				Log.info("Incoming chat " + room + ", participant " + participant + "\n" + message.getBody());
				
				if (!socket.participants.containsKey(participant))	// xmpp participant
				{
					JSONObject json = new JSONObject();
					json.put("type", "chat");
					json.put("username", participant);			
					json.put("source", "");			
					json.put("value", message.getBody());
					json.put("time", System.currentTimeMillis());			
					
					socket.deliver(json.toString());
				}				
			}
		}		
    }

    @Override
    public void deliverRawText(String text)
    {
		Log.debug("deliverRawText\n" + text);	
    }

    @Override
    public boolean validate() {
        return socket.isOpen();
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public PacketDeliverer getPacketDeliverer() {
        if (backupDeliverer == null) {
            backupDeliverer = new OfflinePacketDeliverer();
        }
        return backupDeliverer;
    }

    @Override
    public ConnectionConfiguration getConfiguration() {
        if (configuration == null) {
            final ConnectionManagerImpl connectionManager = ((ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager());
            configuration = connectionManager.getListener( connectionType, true ).generateConnectionConfiguration();
        }
        return configuration;
    }

    @Override
    public boolean isCompressed() {
        return false;
    }

}

package org.ifsoft.galene.openfire;

import org.dom4j.Element;
import org.jivesoftware.openfire.disco.ServerFeaturesProvider;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;

import java.util.*;


/**
 * custom IQ handler for user and group properties JSON request/response.
 */
public class OlMeetIQHandler extends IQHandler implements ServerFeaturesProvider
{
    private final static Logger Log = LoggerFactory.getLogger( OlMeetIQHandler.class );

    public OlMeetIQHandler( )
    {
        super("Online Meetings IQ Handler");
    }

    @Override
    public IQ handleIQ(IQ iq)
    {
        IQ reply = IQ.createResultIQ(iq);

        try {
            Log.info("Online Meetings handleIQ \n" + iq.toString());
            final Element element = iq.getChildElement();

			if (element.getQName().getName().equals("query")) {
				String type = element.attribute("type").getText();
				
				if ("galene".equals(type)) {
					String webUrl = Galene.self.getWebappURL().toString();
					
					if (element.attribute("id") != null) {
						webUrl = webUrl + "/" + element.attribute("id").getText();
					}

					Element childElement = reply.setChildElement("query", "urn:xmpp:http:online-meetings:0");					
					Element initiate = childElement.addElement("initiate");
					initiate.addAttribute("type", type);
					Element url = initiate.addElement("url");
					url.setText(webUrl);			
					
					Element invite  = childElement.addElement("invite", "urn:xmpp:call-invites:0");	
					Element external = invite.addElement("external");	
					external.addAttribute("uri", webUrl);
				} else {
					reply.setError(new PacketError(PacketError.Condition.internal_server_error, PacketError.Type.modify, "online meeting type not supported : " + type ));					
				}					
			}				
			
        } catch(Exception e) {
            Log.error("Online Meetings handleIQ", e);
            reply.setError(new PacketError(PacketError.Condition.internal_server_error, PacketError.Type.modify, e.toString()));
        }
		
        return reply;		
    }

    @Override
    public IQHandlerInfo getInfo()
    {
        return new IQHandlerInfo("query", "urn:xmpp:http:online-meetings:0");
    }
	
    @Override
    public Iterator<String> getFeatures()
    {
        final ArrayList<String> features = new ArrayList<>();
        features.add( "urn:xmpp:http:online-meetings:initiate:0" );
		features.add( "urn:xmpp:http:online-meetings#galene" );
        return features.iterator();
    }	
}

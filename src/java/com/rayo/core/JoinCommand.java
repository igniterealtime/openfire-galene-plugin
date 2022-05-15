package com.rayo.core;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import com.rayo.core.validation.Messages;

public class JoinCommand extends AbstractCallCommand {

	//TODO: MOHO-61
	public class JoinGroup{
		// dummy class used to synchronize complex tasks across the whole joint call
	}
	
	public static final String MEDIA_TYPE = "MEDIA_TYPE";	
	public static final String DIRECTION = "DIRECTION";
	public static final String TO = "TO";
	public static final String TYPE = "TYPE";
	public static final String FORCE = "FORCE";
	
	private String direction = "DUPLEX";

	private String media = "BRIDGE_SHARED";
	
	private Boolean force;

	private String to;
	
	private JoinDestinationType type; 

	private JoinGroup joinGroup;
	
	public JoinCommand() {
		
		this.joinGroup = new JoinGroup();
	}
	
	public String getDirection() {
		return direction;
	}

	public void setDirection(String direction) {
		this.direction = direction;
	}

	public String getMedia() {
		return media;
	}

	public void setMedia(String media) {
		this.media = media;
	}

	public String getTo() {
		return to;
	}

	public void setTo(String to) {
		this.to = to;
	}

	
	public void setType(JoinDestinationType type) {
		this.type = type;
	}

	public JoinDestinationType getType() {
		return type;
	}
	
	public Boolean getForce() {
		return force;
	}

	public void setForce(Boolean force) {
		this.force = force;
	}

	@Override
	public String toString() {

		return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
				.append("callId", getCallId())
				.append("direction", direction).append("media", media)
				.append("to",to)
				.append("type", type).toString();

	}

	public JoinGroup getJoinGroup() {
		return joinGroup;
	}

	public void setJoinGroup(JoinGroup joinGroup) {
		this.joinGroup = joinGroup;
	}
}


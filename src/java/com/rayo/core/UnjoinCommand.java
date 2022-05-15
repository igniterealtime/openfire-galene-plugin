package com.rayo.core;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import com.rayo.core.validation.Messages;

public class UnjoinCommand extends AbstractCallCommand {

	private String from;
	
	private JoinDestinationType type; 

	public String getFrom() {
		return from;
	}

	public void setFrom(String from) {
		this.from = from;
	}

	public JoinDestinationType getType() {
		return type;
	}

	public void setType(JoinDestinationType type) {
		this.type = type;
	}

	@Override
	public String toString() {

		return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
				.append("callId", getCallId())
				.append("from", from)
				.append("type", type).toString();

	}
}

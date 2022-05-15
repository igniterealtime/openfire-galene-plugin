package com.rayo.core;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

import com.rayo.core.validation.Messages;

public class StoppedSpeakingEvent  {

	@NotNull(message=Messages.MISSING_SPEAKER_ID)
	private String speakerId;
	
    public StoppedSpeakingEvent(String speakerId) {
    	this.speakerId = speakerId;
    }

    public String getSpeakerId() {
		return speakerId;
	}
    
    public void setSpeakerId(String speakerId) {
		this.speakerId = speakerId;
	}

	@Override
    public String toString() {

    	return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)    		
			.append("speakerId", getSpeakerId())
    		//.append("mixerId", getMixerId())
    		//.append("participants", getParticipantIds())
    		.toString();
    }
}

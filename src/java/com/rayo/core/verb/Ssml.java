package com.rayo.core.verb;

import java.net.*;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import com.rayo.core.validation.Messages;


public class Ssml {

    private String ssml;

    private String voice;

    public Ssml(String ssml) {
        this.ssml = ssml;
    }

	public String getText() {
        return ssml;
    }

    public void setText(String text) {
        this.ssml = text;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public String getVoice() {
        return voice;
    }    

    public URI toUri() throws java.io.UnsupportedEncodingException {
    	String uriText = ssml.trim();
		
		try {
			if (ssml.startsWith("<speak")) {
				return URI.create("data:" + URLEncoder.encode("application/ssml+xml," + uriText, "UTF-8"));    		
			} else {
				return URI.create("data:" + URLEncoder.encode("application/ssml+xml,<speak>" + uriText + "</speak>", "UTF-8"));
			}
		} catch (Exception e) {
			return null;
		}
    }

    @Override
    public String toString() {

		try {
			return (String) new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)    		
				.append("ssml",ssml)
				.append("voice",getVoice())
				.append("uri",toUri())
				.toString();
		} catch (Exception e) {
			return null;
		}				
    }

}

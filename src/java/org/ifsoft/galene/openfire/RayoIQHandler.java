package org.ifsoft.galene.openfire;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
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

import com.rayo.core.*;
import com.rayo.core.verb.*;
import com.rayo.core.validation.*;
import com.rayo.core.xml.providers.*;

/**
 * custom IQ handler for Rayo
 */
 
public class RayoIQHandler {
    private final static Logger Log = LoggerFactory.getLogger( RayoIQHandler.class );
	
    private static final String RAYO_CORE 	= "urn:xmpp:rayo:1";
    private static final String RAYO_RECORD = "urn:xmpp:rayo:record:1";
    private static final String RAYO_SAY 	= "urn:xmpp:tropo:say:1";
    private static final String RAYO_HANDSET = "urn:xmpp:rayo:handset:1";
	
    private RayoProvider rayoProvider = null;
    private RecordProvider recordProvider = null;
    private SayProvider sayProvider = null;
    private HandsetProvider handsetProvider = null;	
	
	private IQHandler onHookIQHandler = null;
	private IQHandler offHookIQHandler = null;
	private IQHandler privateIQHandler = null;
	private IQHandler publicIQHandler = null;
	private IQHandler muteIQHandler = null;
	private IQHandler unmuteIQHandler = null;
	private IQHandler holdIQHandler = null;

	private IQHandler sayIQHandler = null;
	private IQHandler pauseSayIQHandler = null;
	private IQHandler resumeSayIQHandler = null;

	private IQHandler recordIQHandler = null;
	private IQHandler pauseRecordIQHandler = null;
	private IQHandler resumeRecordIQHandler = null;

	private IQHandler acceptIQHandler = null;
	private IQHandler answerIQHandler = null;
	private IQHandler dialIQHandler = null;
	private IQHandler hangupIQHandler = null;
	private IQHandler redirectIQHandler = null;
	private IQHandler dtmfIQHandler = null;

	
	public void startHandler() {
		XMPPServer server = XMPPServer.getInstance();
		
		server.getIQDiscoInfoHandler().addServerFeature(RAYO_CORE);
       	rayoProvider = new RayoProvider();
        rayoProvider.setValidator(new Validator());

		server.getIQDiscoInfoHandler().addServerFeature(RAYO_RECORD);
        recordProvider = new RecordProvider();
        recordProvider.setValidator(new Validator());

		server.getIQDiscoInfoHandler().addServerFeature(RAYO_SAY);
        sayProvider = new SayProvider();
        sayProvider.setValidator(new Validator());

		server.getIQDiscoInfoHandler().addServerFeature(RAYO_HANDSET);
        handsetProvider = new HandsetProvider();
        handsetProvider.setValidator(new Validator());		

		onHookIQHandler 	= new OnHookIQHandler(); server.getIQRouter().addHandler(onHookIQHandler);
		offHookIQHandler 	= new OffHookIQHandler(); server.getIQRouter().addHandler(offHookIQHandler);
		privateIQHandler 	= new PrivateIQHandler(); server.getIQRouter().addHandler(privateIQHandler);
		publicIQHandler 	= new PublicIQHandler(); server.getIQRouter().addHandler(publicIQHandler);
		muteIQHandler 		= new MuteIQHandler(); server.getIQRouter().addHandler(muteIQHandler);
		unmuteIQHandler		= new UnmuteIQHandler(); server.getIQRouter().addHandler(unmuteIQHandler);
		holdIQHandler 		= new HoldIQHandler(); server.getIQRouter().addHandler(holdIQHandler);

		recordIQHandler 		= new RecordIQHandler(); server.getIQRouter().addHandler(recordIQHandler);
		pauseRecordIQHandler 	= new PauseRecordIQHandler(); server.getIQRouter().addHandler(pauseRecordIQHandler);
		resumeRecordIQHandler 	= new ResumeRecordIQHandler(); server.getIQRouter().addHandler(resumeRecordIQHandler);

		sayIQHandler 		= new SayIQHandler(); server.getIQRouter().addHandler(sayIQHandler);
		pauseSayIQHandler	= new PauseSayIQHandler(); server.getIQRouter().addHandler(pauseSayIQHandler);
		resumeSayIQHandler 	= new ResumeSayIQHandler(); server.getIQRouter().addHandler(resumeSayIQHandler);

		acceptIQHandler 	= new AcceptIQHandler(); server.getIQRouter().addHandler(acceptIQHandler);
		answerIQHandler 	= new AnswerIQHandler(); server.getIQRouter().addHandler(answerIQHandler);
		dialIQHandler 		= new DialIQHandler(); server.getIQRouter().addHandler(dialIQHandler);
		hangupIQHandler 	= new HangupIQHandler(); server.getIQRouter().addHandler(hangupIQHandler);
		redirectIQHandler	= new RedirectIQHandler(); server.getIQRouter().addHandler(redirectIQHandler);
		dtmfIQHandler 		= new DtmfIQHandler(); server.getIQRouter().addHandler(dtmfIQHandler);
	}

	public void stopHandler() {
		XMPPServer server = XMPPServer.getInstance();

		if (onHookIQHandler != null) {server.getIQRouter().removeHandler(onHookIQHandler); onHookIQHandler = null;}
		if (offHookIQHandler != null) {server.getIQRouter().removeHandler(offHookIQHandler); offHookIQHandler = null;}
		if (privateIQHandler != null) {server.getIQRouter().removeHandler(privateIQHandler); privateIQHandler = null;}
		if (publicIQHandler != null) {server.getIQRouter().removeHandler(publicIQHandler); publicIQHandler = null;}
		if (muteIQHandler != null) {server.getIQRouter().removeHandler(muteIQHandler); muteIQHandler = null;}
		if (unmuteIQHandler != null) {server.getIQRouter().removeHandler(unmuteIQHandler); unmuteIQHandler = null;}
		if (holdIQHandler != null) {server.getIQRouter().removeHandler(holdIQHandler); holdIQHandler = null;}

		if (sayIQHandler != null) {server.getIQRouter().removeHandler(sayIQHandler); sayIQHandler = null;}
		if (pauseSayIQHandler != null) {server.getIQRouter().removeHandler(pauseSayIQHandler); pauseSayIQHandler = null;}
		if (resumeSayIQHandler != null) {server.getIQRouter().removeHandler(resumeSayIQHandler); resumeSayIQHandler = null;}

		if (acceptIQHandler != null) {server.getIQRouter().removeHandler(acceptIQHandler); acceptIQHandler = null;}
		if (answerIQHandler != null) {server.getIQRouter().removeHandler(answerIQHandler); answerIQHandler = null;}
		if (dialIQHandler != null) {server.getIQRouter().removeHandler(dialIQHandler); dialIQHandler = null;}
		if (hangupIQHandler != null) {server.getIQRouter().removeHandler(hangupIQHandler); hangupIQHandler = null;}
		if (redirectIQHandler != null) {server.getIQRouter().removeHandler(redirectIQHandler); redirectIQHandler = null;}
		if (dtmfIQHandler != null) {server.getIQRouter().removeHandler(dtmfIQHandler); dtmfIQHandler = null;}
	
	}
	
    private class OnHookIQHandler extends IQHandler
    {
        public OnHookIQHandler() { super("Rayo: XEP 0327 - Onhook");}

        @Override public IQ handleIQ(IQ iq)  {try {return handleIQGet(iq);} catch(Exception e) {return null;} }
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("onhook", RAYO_HANDSET); }
    }

    private class OffHookIQHandler extends IQHandler
    {
        public OffHookIQHandler() { super("Rayo: XEP 0327 - Offhook");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("offhook", RAYO_HANDSET); }
    }
    private class PrivateIQHandler extends IQHandler
    {
        public PrivateIQHandler() { super("Rayo: XEP 0327 - Private");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("private", RAYO_HANDSET); }
    }
    private class PublicIQHandler extends IQHandler
    {
        public PublicIQHandler() { super("Rayo: XEP 0327 - Public");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("public", RAYO_HANDSET); }
    }

    private class MuteIQHandler extends IQHandler
    {
        public MuteIQHandler() { super("Rayo: XEP 0327 - Mute");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("mute", RAYO_HANDSET); }
    }

    private class UnmuteIQHandler extends IQHandler
    {
        public UnmuteIQHandler() { super("Rayo: XEP 0327 - Unmute");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("unmute", RAYO_HANDSET); }
    }

    private class HoldIQHandler extends IQHandler
    {
        public HoldIQHandler() { super("Rayo: XEP 0327 - Hold");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("hold", RAYO_HANDSET); }
    }


    private class RecordIQHandler extends IQHandler
    {
        public RecordIQHandler() { super("Rayo: XEP 0327 - Record");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("record", RAYO_RECORD); }
    }

    private class PauseRecordIQHandler extends IQHandler
    {
        public PauseRecordIQHandler() { super("Rayo: XEP 0327 - Pause Record");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("pause", RAYO_RECORD); }
    }

    private class ResumeRecordIQHandler extends IQHandler
    {
        public ResumeRecordIQHandler() { super("Rayo: XEP 0327 - Resume Record");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("resume", RAYO_RECORD); }
    }



    private class SayIQHandler extends IQHandler
    {
        public SayIQHandler() { super("Rayo: XEP 0327 - Say (text to speech)");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("say", RAYO_SAY); }
    }

    private class PauseSayIQHandler extends IQHandler
    {
        public PauseSayIQHandler() { super("Rayo: XEP 0327 - Pause Say");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("pause", RAYO_SAY); }
    }

    private class ResumeSayIQHandler extends IQHandler
    {
        public ResumeSayIQHandler() { super("Rayo: XEP 0327 - Resume Say");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("resume", RAYO_SAY); }
    }

    private class AcceptIQHandler extends IQHandler
    {
        public AcceptIQHandler() { super("Rayo: XEP 0327 - Accept");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("accept", RAYO_CORE); }
    }

    private class AnswerIQHandler extends IQHandler
    {
        public AnswerIQHandler() { super("Rayo: XEP 0327 - Answer");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("answer", RAYO_CORE); }
    }

    private class DialIQHandler extends IQHandler
    {
        public DialIQHandler() { super("Rayo: XEP 0327 - Dial");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("dial", RAYO_CORE); }
    }

    private class HangupIQHandler extends IQHandler
    {
        public HangupIQHandler() { super("Rayo: XEP 0327 - Hangup");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("hangup", RAYO_CORE); }
    }

    private class RedirectIQHandler extends IQHandler
    {
        public RedirectIQHandler() { super("Rayo: XEP 0327 - Redirect");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("redirect", RAYO_CORE); }
    }

    private class DtmfIQHandler extends IQHandler
    {
        public DtmfIQHandler() { super("Rayo: XEP 0327 - DTMF");}

        @Override public IQ handleIQ(IQ iq) {try {return handleIQGet(iq);} catch(Exception e) { return null;}}
        @Override public IQHandlerInfo getInfo() { return new IQHandlerInfo("dtmf", RAYO_CORE); }
    }	
	
    private IQ handleIQGet(IQ iq) throws Exception {

		Log.info("RayoComponent handleIQGet \n" + iq.toString());

        final Element element = iq.getChildElement();
        final String namespace = element.getNamespaceURI();

        try {

			if (RAYO_HANDSET.equals(namespace)) {
				IQ reply = null;

				Object object = handsetProvider.fromXML(element);

				if (object instanceof OnHookCommand) {
					OnHookCommand command = (OnHookCommand) object;
					reply = handleOnOffHookCommand(command, iq);

				} else if (object instanceof OffHookCommand) {
					OffHookCommand command = (OffHookCommand) object;
					reply = handleOnOffHookCommand(command, iq);

				} else if (object instanceof MuteCommand) {
					reply = handleMuteCommand((MuteCommand) object, iq);

				} else if (object instanceof UnmuteCommand) {
					reply = handleMuteCommand((UnmuteCommand) object, iq);

				} else if (object instanceof HoldCommand) {
					reply = handleHoldCommand((HoldCommand) object, iq);

				} else if (object instanceof PrivateCommand) {
					reply = handlePrivateCommand(object, iq);

				} else if (object instanceof PublicCommand) {
					reply = handlePrivateCommand(object, iq);

				} else if (object instanceof CreateSpeakerCommand) {
					reply = handleCreateSpeakerCommand(object, iq);

				} else if (object instanceof DestroySpeakerCommand) {
					reply = handleDestroySpeakerCommand(object, iq);

				} else if (object instanceof PutOnSpeakerCommand) {
					reply = handleOnOffSpeakerCommand(object, iq, true);

				} else if (object instanceof TakeOffSpeakerCommand) {
					reply = handleOnOffSpeakerCommand(object, iq, false);

				} else if (object instanceof TalkCommand) {
					reply = handleOnOffTalkCommand(object, iq, false);

				} else if (object instanceof UntalkCommand) {
					reply = handleOnOffTalkCommand(object, iq, true);
				}
				return reply;
			}

			if (RAYO_RECORD.equals(namespace)) {
				IQ reply = null;

				Object object = recordProvider.fromXML(element);

				if (object instanceof com.rayo.core.verb.Record) {
					reply = handleRecord((com.rayo.core.verb.Record) object, iq);

				} else if (object instanceof PauseCommand) {
					reply = handlePauseRecordCommand(true, iq);

				} else if (object instanceof ResumeCommand) {
					reply = handlePauseRecordCommand(false, iq);
				}
				return reply;
			}

			if (RAYO_SAY.equals(namespace)) {
				IQ reply = null;

				Object object = sayProvider.fromXML(element);

				if (object instanceof Say) {
					reply = handleSay((Say) object, iq);

				} else if (object instanceof PauseCommand) {
					reply = handlePauseSayCommand(true, iq);

				} else if (object instanceof ResumeCommand) {
					reply = handlePauseSayCommand(false, iq);
				}
				return reply;
			}

			if (RAYO_CORE.equals(namespace)) {
				IQ reply = null;

				Object object = rayoProvider.fromXML(element);

				if (object instanceof JoinCommand) {
					reply = handleJoinCommand((JoinCommand) object, iq);

				} else if (object instanceof UnjoinCommand) {
					reply = handleUnjoinCommand((UnjoinCommand) object, iq);

				} else if (object instanceof AcceptCommand) {
					reply = handleAcceptCommand((AcceptCommand) object, iq);

				} else if (object instanceof AnswerCommand) {
					reply = handleAnswerCommand((AnswerCommand) object, iq);

				} else if (object instanceof HangupCommand) {
					reply = handleHangupCommand(iq);

				} else if (object instanceof RejectCommand) {
						// implemented as hangup on client

				} else if (object instanceof RedirectCommand) {
					RedirectCommand redirect = (RedirectCommand) object;
					DialCommand dial = new DialCommand();
					dial.setTo(redirect.getTo());
					dial.setFrom(new URI("xmpp:" + iq.getFrom()));
					dial.setHeaders(redirect.getHeaders());

					reply = handleDialCommand((DialCommand) dial, iq, true);

				} else if (object instanceof DialCommand) {
					reply = handleDialCommand((DialCommand) object, iq, false);

				} else if (object instanceof StopCommand) {

				} else if (object instanceof DtmfCommand) {
					reply = handleDtmfCommand((DtmfCommand) object, iq);

				} else if (object instanceof DestroyMixerCommand) {

				}

				return reply;
			}
			return null; // feature not implemented.

        } catch (Exception e) {
            e.printStackTrace();

            final IQ reply = IQ.createResultIQ(iq);
            reply.setError(PacketError.Condition.internal_server_error);
            return reply;
        }
    }
	
	private IQ handleHoldCommand(Object object, IQ iq)
	{
		Log.info("RayoComponent handleHoldCommand");

		IQ reply = IQ.createResultIQ(iq);
		String callId = iq.getTo().getNode();		// far party

		return reply;
	}

	private IQ handleMuteCommand(Object object, IQ iq)
	{
		Log.info("RayoComponent handleMuteCommand");

		boolean muted = object instanceof MuteCommand;

		IQ reply = IQ.createResultIQ(iq);
		String callId = JID.escapeNode(iq.getFrom().toString());
		
		return reply;
	}

	private IQ handlePrivateCommand(Object object, IQ iq)
	{
		Log.info("RayoComponent handlePrivateCommand");

		boolean privateCall = object instanceof PrivateCommand;

		IQ reply = IQ.createResultIQ(iq);
		String callId = JID.escapeNode(iq.getFrom().toString());

		return reply;
	}

	private IQ handleOnOffHookCommand(Object object, IQ iq)
	{
		Log.info("RayoComponent handleOnOffHookCommand");

		IQ reply = IQ.createResultIQ(iq);
		String handsetId = JID.escapeNode(iq.getFrom().toString());

		if (object instanceof OnHookCommand) {	
		
		}
		
		return reply;
	}	

	private IQ handleOnOffTalkCommand(Object object, IQ iq, boolean mute)
	{
		Log.info("RayoComponent handleOnOffTalkCommand");

		IQ reply = IQ.createResultIQ(iq);
		String callId = iq.getTo().getNode();
		
		return reply;
	}	
	
	private IQ handleOnOffSpeakerCommand(Object object, IQ iq, boolean flag)
	{
		Log.info("RayoComponent handleOnOffSpeakerCommand");

		IQ reply = IQ.createResultIQ(iq);
		String callId = iq.getTo().getNode();	
		
		return reply;
	}	

	private IQ handleDestroySpeakerCommand(Object object, IQ iq)
	{
		Log.info("RayoComponent handleDestroySpeakerCommand");

		IQ reply = IQ.createResultIQ(iq);
		DestroySpeakerCommand speaker = (DestroySpeakerCommand) object;	
		
		return reply;
	}	

	private IQ handleCreateSpeakerCommand(Object object, IQ iq)
	{
		Log.info("RayoComponent handleCreateSpeakerCommand");

		IQ reply = IQ.createResultIQ(iq);
		CreateSpeakerCommand speaker = (CreateSpeakerCommand) object;	

		return reply;
	}
		
	private IQ handleRecord(com.rayo.core.verb.Record command, IQ iq)
	{
		Log.info("RayoComponent handleRecord " + iq.getFrom());

		IQ reply = IQ.createResultIQ(iq);
		String callId = iq.getTo().getNode();
		final String uri = command.getTo().toString();	

		return reply;
	}

	private IQ handlePauseRecordCommand(boolean flag, IQ iq)
	{
		Log.info("RayoComponent handlePauseRecordCommand " + iq.getFrom() + " " + iq.getTo());

		IQ reply = IQ.createResultIQ(iq);
		String callId = iq.getTo().getNode();	
		
		return reply;
	}

	private IQ handleSay(Say command, IQ iq)
	{
		Log.info("RayoComponent handleSay " + iq.getFrom());

		IQ reply = IQ.createResultIQ(iq);
		final String entityId = iq.getTo().getNode();
		final String treatmentId = command.getPrompt().getText();	
		
		return reply;
	}	

	private IQ handlePauseSayCommand(boolean flag, IQ iq)
	{
		Log.info("RayoComponent handlePauseSayCommand " + iq.getFrom() + " " + iq.getTo());

		IQ reply = IQ.createResultIQ(iq);
		
		return reply;
	}	

	private IQ handleAcceptCommand(AcceptCommand command, IQ iq)
	{
		Map<String, String> headers = command.getHeaders();

		String callId = iq.getTo().getNode();	// destination JID escaped
		String callerId = headers.get("caller_id"); // source JID
		String mixer = headers.get("mixer_name");

		Log.info("RayoComponent handleAcceptCommand " + callerId + " " + callId + " " + mixer);

		IQ reply = IQ.createResultIQ(iq);	
		
		return reply;
	}

	private IQ handleAnswerCommand(AnswerCommand command, IQ iq)
	{
		Map<String, String> headers = command.getHeaders();

		IQ reply = IQ.createResultIQ(iq);

		String callId = iq.getTo().getNode(); // destination JID escaped
		String callerId = headers.get("caller_id"); // source JID

		Log.info("RayoComponent AnswerCommand " + callerId + " " + callId);	
		
		return reply;
	}	

	private IQ handleHangupCommand(IQ iq)
	{
		String callId = iq.getTo().getNode();
		Log.info("RayoComponent handleHangupCommand " + iq.getFrom() + " " + callId);

		IQ reply = IQ.createResultIQ(iq);

		return reply;
	}	

	private IQ handleDtmfCommand(DtmfCommand command, IQ iq)
	{
		Log.info("RayoComponent handleDtmfCommand " + iq.getFrom());
		IQ reply = IQ.createResultIQ(iq);

		return reply;
	}	

	private IQ handleJoinCommand(JoinCommand command, IQ iq)
	{
		Log.info("RayoComponent handleJoinCommand " + iq.getFrom());

        IQ reply = IQ.createResultIQ(iq);

		return reply;
	}	

	private IQ handleUnjoinCommand(UnjoinCommand command, IQ iq)
	{
		Log.info("RayoComponent handleUnjoinCommand " + iq.getFrom());

        IQ reply = IQ.createResultIQ(iq);

		return reply;
	}

	private IQ handleDialCommand(DialCommand command, IQ iq, boolean transferCall)
	{
		Log.info("RayoComponent handleHandsetDialCommand " + iq.getFrom());

        IQ reply = IQ.createResultIQ(iq);

		Map<String, String> headers = command.getHeaders();
		String from = command.getFrom().toString();
		String to = command.getTo().toString();

		return reply;
	}

		
}



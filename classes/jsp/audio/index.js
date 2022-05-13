let serverConnection;

window.addEventListener("unload", function () {
	console.debug("unload");	
	if (serverConnection) serverConnection.close();
});

window.onload = async function() {	
    serverConnection = new ServerConnection();
    serverConnection.onconnected = gotConnected;
    serverConnection.onpeerconnection = onPeerConnection;
    serverConnection.onclose = gotClose;
    serverConnection.ondownstream = gotDownStream;
    serverConnection.onuser = gotUser;
    serverConnection.onjoined = gotJoined;
    serverConnection.onchat = addToChatbox;
    serverConnection.onusermessage = gotUserMessage;
	
	const url = (window.location.protocol == "https:" ? "wss:" : "ws:") + '//' + window.location.host + "/ws/";
	const username = urlParam("username");
    const jid = username + "@localhost";
    const password = "Welcome123";

    const connection = new Strophe.Connection(url);
	
    connection.connect(jid, password, async (status) => {
        console.debug("XMPPConnection.connect", status);

        if (status === Strophe.Status.CONNECTED) {
            connection.send($pres());
			await serverConnection.connect(connection);
        }
        else

        if (status === Strophe.Status.DISCONNECTED) {
			serverConnection.close();
        }
    });
}

async function enableButton() {
	const talk = document.getElementById("talk");
	talk.style.display = "";

	talk.addEventListener("click", async function (e) {
		console.debug("talk clicked");
		
		if (talk.innerHTML == "Start") {	
			talk.innerHTML = "Stop";
			
			let constraints = {audio: true};
			let stream = null;
			
			try {
				stream = await navigator.mediaDevices.getUserMedia(constraints);
			} catch(e) {
				console.error("talk clicked", e);
				return;
			}

			let c;

			try {
				c = newUpStream();
				serverConnection.groupAction('record');
			} catch(e) {
				console.error("talk clicked", e);
				return;
			}		

			setUpStream(c, stream);
			await setMedia(c, true);

		} else {
			talk.innerHTML = "Start";				
			closeUpMedia();	
		}			
	});		
}

function urlParam (name) {
	var results = new RegExp('[\\?&]' + name + '=([^&#]*)').exec(window.location.href);
	if (!results) { return undefined; }
	return unescape(results[1] || undefined);
}

async function gotConnected() {
	console.debug("onPeerConnection");	
	
	const username = urlParam("username");
	const pw = "";
	const group = "public/" + urlParam("group");	

    try {
        await serverConnection.join(group, username, pw);
    } catch(e) {
        console.error(e);
        serverConnection.close();
    }
}

function gotUser(id, kind) {
	console.debug("gotUser", id, kind);
}

async function gotJoined(kind, group, perms, status, data, message) {
	console.debug("gotJoined", kind, group, perms, status, data, message);
	
    switch(kind) {
    case 'join':
        serverConnection.request({'':['audio']});
		enableButton();
        break;
    case 'change':
        break;
    case 'redirect':
        serverConnection.close();
        break;
    case 'fail':
        console.error("failed to join")
        break;	
	}
}

function stopStream(s) {
	console.debug("stopStream", s);
	
    s.getTracks().forEach(t => {
        try {
            t.stop();
        } catch(e) {
            console.warn(e);
        }
    });
}

function setUpStream(c, stream) {
	console.debug("setUpStream", c, stream);	
	
    if(c.stream != null)
        throw new Error("Setting nonempty stream");

    c.setStream(stream);

    c.onclose = replace => {

        if(!replace) {
            stopStream(c.stream);
            if(c.userdata.onclose)
                c.userdata.onclose.call(c);
            delMedia(c.localId);
        }
    }

    function addUpTrack(t) {
        if(c.label === 'camera') {
            if(t.kind == 'audio') {

            } else if(t.kind == 'video') {

            }
        }
        t.onended = e => {
            stream.onaddtrack = null;
            stream.onremovetrack = null;
            c.close();
        };

        let encodings = [];
        let tr = c.pc.addTransceiver(t, {
            direction: 'sendonly',
            streams: [stream],
            sendEncodings: encodings,
        });

        // Firefox workaround
        function match(a, b) {
            if(!a || !b)
                return false;
            if(a.length !== b.length)
                return false;
            for(let i = 0; i < a.length; i++) {
                if(a.maxBitrate !== b.maxBitrate)
                    return false;
            }
            return true;
        }

        let p = tr.sender.getParameters();
		
        if (!p || !match(p.encodings, encodings)) {
            p.encodings = encodings;
            tr.sender.setParameters(p);
        }
    }

    // c.stream might be different from stream if there's a filter
    c.stream.getTracks().forEach(addUpTrack);

    stream.onaddtrack = function(e) {
        addUpTrack(e.track);
    };

    stream.onremovetrack = function(e) {
        let t = e.track;
        let sender;
		
        c.pc.getSenders().forEach(s => {
            if(s.track === t)
                sender = s;
        });
        if(sender) {
            c.pc.removeTrack(sender);
        } else {
            console.warn('Removing unknown track');
        }

        let found = false;
        c.pc.getSenders().forEach(s => {
            if(s.track)
                found = true;
        });
        if(!found) {
            stream.onaddtrack = null;
            stream.onremovetrack = null;
            c.close();
        }
    };
}

function onPeerConnection() {
	console.debug("onPeerConnection");
    return null;
}

function gotClose(code, reason) {
	console.debug("gotClose", code, reason);
	
    closeUpMedia();

    if(code != 1000) {
        console.warn('Socket close', code, reason);
    }
}

function gotDownStats(stats) {
	console.debug("gotDownStats", stats);	
}

function closeUpMedia(label) {
	console.debug("closeUpMedia", label);
	
    for(let id in serverConnection.up) {
        let c = serverConnection.up[id];
        if(label && c.label !== label)
            continue
        c.close();
    }
}

function gotDownStream(c) {
	console.debug("gotDownStream", c);
	
    c.onclose = function(replace) {
        if(!replace)
            delMedia(c.localId);
    };
    c.onerror = function(e) {
        console.error(e);
    };
    c.ondowntrack = function(track, transceiver, label, stream) {
        setMedia(c, false);
    };
    c.onnegotiationcompleted = function() {
        resetMedia(c);
    }
    c.onstatus = function(status) {
        setMediaStatus(c);
    };
	
    c.onstats = gotDownStats;
    setMedia(c, false);
}

function setMediaStatus(c) {
    let state = c && c.pc && c.pc.iceConnectionState;
    let good = state === 'connected' || state === 'completed';

    let media = document.getElementById('media-' + c.localId);
    if(!media) {
        console.warn('Setting status of unknown media.');
        return;
    }
    if(good) {
        media.classList.remove('media-failed');
        if(c.userdata.play) {
            if(media instanceof HTMLMediaElement)
                media.play().catch(e => {
                    console.error(e);
                });
            delete(c.userdata.play);
        }
    } else {
        media.classList.add('media-failed');
    }
}

function delMedia(localId) {
	console.debug("delMedia", localId);
	
    let mediadiv = document.getElementById('peers');
    let peer = document.getElementById('peer-' + localId);
    
	if(!peer)
        throw new Error('Removing unknown media');

    let media = document.getElementById('media-' + localId);

    media.srcObject = null;
    mediadiv.removeChild(peer);
}

function resetMedia(c) {
    let media = document.getElementById('media-' + c.localId);
	
    if(!media) {
        console.error("Resetting unknown media element")
        return;
    }
    media.srcObject = media.srcObject;
}

async function setMedia(c, isUp, mirror, video) {
	console.debug("setMedia", c, isUp, mirror, video);
	
    let peersdiv = document.getElementById('peers');

    let div = document.getElementById('peer-' + c.localId);
	
    if (!div) {
        div = document.createElement('div');
        div.id = 'peer-' + c.localId;
        div.classList.add('peer');
        peersdiv.appendChild(div);
    }

    let media = document.getElementById('media-' + c.localId);
	
    if(!media) {
        if (video) {
            media = video;
        } else {
            media = document.createElement('audio');
            if(isUp)
                media.muted = true;
        }

        media.classList.add('media');
        media.autoplay = true;
        media.playsInline = true;
        media.id = 'media-' + c.localId;
        div.appendChild(media);
    }

    if(mirror)
        media.classList.add('mirror');
    else
        media.classList.remove('mirror');

    if(!video && media.srcObject !== c.stream)
        media.srcObject = c.stream;

    let label = document.getElementById('label-' + c.localId);
	
    if(!label) {
        label = document.createElement('div');
        label.id = 'label-' + c.localId;
        label.classList.add('label');
        div.appendChild(label);
    }
}

function addToChatbox(peerId, dest, nick, time, privileged, history, kind, message) {
	console.debug("addToChatbox", peerId, dest, nick, time, privileged, history, kind, message);		
    return message;	
}

function gotUserMessage(id, dest, username, time, privileged, kind, message) {
	console.debug("gotUserMessage", id, dest, username, time, privileged, kind, message);	
}

function newUpStream(localId) {
    let c = serverConnection.newUpStream(localId);
    c.onstatus = function(status) {
        setMediaStatus(c);
    };
    c.onerror = function(e) {
        console.error(e);
    };
    return c;
}
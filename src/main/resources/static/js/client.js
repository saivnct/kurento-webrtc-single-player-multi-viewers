/*
 * (C) 2023 Giangbb Studio (https://github.com/saivnct)
 *
 *
 */

let ws = new WebSocket('wss://' + location.host + '/viewer');
let video;
let webRtcPeer;

window.onload = function() {
	console = new Console();
	video = document.getElementById('video');
	disableStopButton();
}

window.onbeforeunload = function() {
	ws.close();
}

ws.onmessage = function(message) {
	const parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
	case 'viewerResponse':
		viewerResponse(parsedMessage);
		break;
	case 'iceCandidate':
		webRtcPeer.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error)
				return console.error('Error adding candidate: ' + error);
		});
		break;
	case 'stopCommunication':
		dispose();
		break;
	default:
		console.error('Unrecognized message', parsedMessage);
	}
}

function viewerResponse(message) {
	if (message.response !== 'accepted') {
		const errorMsg = message.message ? message.message : 'Unknow error';
		console.error('Call not accepted for the following reason: ' + errorMsg);
		dispose();
	} else {
		webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
			if (error)
				return console.error(error);
		});
	}
}

function viewer() {
	if (!webRtcPeer) {
		showSpinner(video);

		const mode = $('input[name="mode"]:checked').val();
		console.log('Creating WebRtcPeer in ' + mode + ' mode and generating local sdp offer ...');

		// Video and audio by default
		const userMediaConstraints = {
			audio : true,
			video : true
		}

		if (mode === 'video-only') {
			userMediaConstraints.audio = false;
		} else if (mode === 'audio-only') {
			userMediaConstraints.video = false;
		}

		const options = {
			remoteVideo : video,
			mediaConstraints : userMediaConstraints,
			onicecandidate : onIceCandidate
		}

		console.info('User media constraints' + userMediaConstraints);

		webRtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options, function(error) {
			if (error)
				return console.error(error);
			this.generateOffer(onOfferViewer);
		});

		enableStopButton();
	}
}

function onOfferViewer(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');
	console.info('Invoking SDP offer callback function ' + location.host);

	const videourl = document.getElementById('videourl').value;

	const message = {
		id : 'viewer',
		videourl: videourl,
		sdpOffer : offerSdp
	}
	sendMessage(message);
}

function onIceCandidate(candidate) {
	console.log("Local candidate" + JSON.stringify(candidate));

	const message = {
		id : 'onIceCandidate',
		candidate : candidate
	};
	sendMessage(message);
}

function stop() {
	const message = {
		id : 'stop'
	}
	sendMessage(message);
	dispose();
}

function dispose() {
	if (webRtcPeer) {
		webRtcPeer.dispose();
		webRtcPeer = null;
	}
	hideSpinner(video);

	disableStopButton();
}

function disableStopButton() {
	enableButton('#viewer', 'viewer()');
	enableButton("[name='mode']");
	disableButton('#stop');
}

function enableStopButton() {
	disableButton('#viewer');
	disableButton("[name='mode']");
	enableButton('#stop', 'stop()');
}

function disableButton(id) {
	$(id).attr('disabled', true);
	$(id).removeAttr('onclick');
}

function enableButton(id, functionName) {
	$(id).attr('disabled', false);
	if (functionName) {
		$(id).attr('onclick', functionName);
	}
}

function sendMessage(message) {
	const jsonMessage = JSON.stringify(message);
	console.log('Sending message: ' + jsonMessage);
	ws.send(jsonMessage);
}

function showSpinner() {
	for (let i = 0; i < arguments.length; i++) {
		arguments[i].poster = './img/transparent-1px.png';
		arguments[i].style.background = 'center transparent url("./img/spinner.gif") no-repeat';
	}
}

function hideSpinner() {
	for (let i = 0; i < arguments.length; i++) {
		arguments[i].src = '';
		arguments[i].poster = './img/webrtc.png';
		arguments[i].style.background = '';
	}
}

/**
 * Lightbox utility (to display media pipeline image in a modal dialog)
 */
$(document).delegate('*[data-toggle="lightbox"]', 'click', function(event) {
	event.preventDefault();
	$(this).ekkoLightbox();
});

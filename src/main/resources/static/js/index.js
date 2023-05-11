/*
 * (C) 2023 Giangbb Studio (https://github.com/saivnct)
 *
 *
 */

let ws = new WebSocket('wss://' + location.host + '/player');
let video;
let webRtcPeer;
let state = null;
let isSeekable = false;

let I_CAN_START = 0;
let I_CAN_STOP = 1;
let I_AM_STARTING = 2;

window.onload = function() {
	console = new Console();
	video = document.getElementById('video');
	setState(I_CAN_START);
}

window.onbeforeunload = function() {
	ws.close();
}

ws.onmessage = function(message) {
	const parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
	case 'startResponse':
		startResponse(parsedMessage);
		break;
	case 'error':
		if (state === I_AM_STARTING) {
			setState(I_CAN_START);
		}
		onError('Error message from server: ' + parsedMessage.message);
		break;
	case 'playEnd':
		playEnd();
		break;
	case 'videoInfo':
		showVideoData(parsedMessage);
		break;
	case 'iceCandidate':
		webRtcPeer.addIceCandidate(parsedMessage.candidate, function(error) {
			if (error)
				return console.error('Error adding candidate: ' + error);
		});
		break;
	case 'seek':
		console.log (parsedMessage.message);
		break;
	case 'position':
		document.getElementById("videoPosition").value = parsedMessage.position;
		break;
	default:
		if (state === I_AM_STARTING) {
			setState(I_CAN_START);
		}
		onError('Unrecognized message', parsedMessage);
	}
}

function start() {
	// Disable start button
	setState(I_AM_STARTING);
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
		webRtcPeer.generateOffer(onOfferPresenter);
	});
}

function onOfferPresenter(error, offerSdp) {
	if (error)
		return console.error('Error generating the offer');

	console.info('Invoking SDP offer callback function ' + location.host);

	const message = {
		id : 'start',
		sdpOffer : offerSdp,
		videourl : document.getElementById('videourl').value
	}
	sendMessage(message);
}

function onError(error) {
	console.error(error);
}

function onIceCandidate(candidate) {
	console.log('Local candidate' + JSON.stringify(candidate));

	const message = {
		id : 'onIceCandidate',
		candidate : candidate
	}
	sendMessage(message);
}

function startResponse(message) {
	if (message.response !== 'accepted') {
		const errorMsg = message.message ? message.message : 'Unknow error';
		console.error('Call not accepted for the following reason: ' + errorMsg);
		setState(I_CAN_START);
		if (webRtcPeer) {
			webRtcPeer.dispose();
			webRtcPeer = null;
		}
		hideSpinner(video);
	} else {
		setState(I_CAN_STOP);
		console.log('SDP answer received from server. Processing ...');
		webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
			if (error)
				return console.error(error);
		});
	}
}

function pause() {
	togglePause()
	console.log('Pausing video ...');
	const message = {
		id : 'pause'
	}
	sendMessage(message);
}

function resume() {
	togglePause()
	console.log('Resuming video ...');
	const message = {
		id : 'resume'
	}
	sendMessage(message);
}

function stop() {
	console.log('Stopping video ...');
	setState(I_CAN_START);
	if (webRtcPeer) {
		webRtcPeer.dispose();
		webRtcPeer = null;

		const message = {
			id : 'stop'
		}
		sendMessage(message);
	}
	hideSpinner(video);
}

function debugDot() {
	console.log('Generate debug DOT file ...');
	sendMessage({
		id: 'debugDot'
	});
}

function playEnd() {
	setState(I_CAN_START);
	if (webRtcPeer) {
		webRtcPeer.dispose();
		webRtcPeer = null;
	}
	hideSpinner(video);
}

function doSeek() {
	const message = {
		id : 'doSeek',
		position: document.getElementById("seekPosition").value
	}
	sendMessage(message);
}

function getPosition() {
	const message = {
		id : 'getPosition'
	}
	sendMessage(message);
}

function showVideoData(parsedMessage) {
	//Show video info
	isSeekable = parsedMessage.isSeekable;
	if (isSeekable) {
		document.getElementById('isSeekable').value = "true";
		enableButton('#doSeek', 'doSeek()');
	} else {
		document.getElementById('isSeekable').value = "false";
	}

	document.getElementById('initSeek').value = parsedMessage.initSeekable;
	document.getElementById('endSeek').value = parsedMessage.endSeekable;
	document.getElementById('duration').value = parsedMessage.videoDuration;

	enableButton('#getPosition', 'getPosition()');
}

function setState(nextState) {
	switch (nextState) {
	case I_CAN_START:
		enableButton('#start', 'start()');
		disableButton('#pause');
		disableButton('#stop');
		disableButton('#debugDot');
		enableButton('#videourl');
		enableButton("[name='mode']");
		disableButton('#getPosition');
		disableButton('#doSeek');
		break;

	case I_CAN_STOP:
		disableButton('#start');
		enableButton('#pause', 'pause()');
		enableButton('#stop', 'stop()');
		enableButton('#debugDot', 'debugDot()');
		disableButton('#videourl');
		disableButton("[name='mode']");
		break;

	case I_AM_STARTING:
		disableButton('#start');
		disableButton('#pause');
		disableButton('#stop');
		disableButton('#debugDot');
		disableButton('#videourl');
		disableButton('#getPosition');
		disableButton('#doSeek');
		disableButton("[name='mode']");
		break;

	default:
		onError('Unknown state ' + nextState);
		return;
	}
	state = nextState;
}

function sendMessage(message) {
	const jsonMessage = JSON.stringify(message);
	console.log('Sending message: ' + jsonMessage);
	ws.send(jsonMessage);
}

function togglePause() {
	const pauseText = $("#pause-text").text();
	if (pauseText === " Resume ") {
		$("#pause-text").text(" Pause ");
		$("#pause-icon").attr('class', 'glyphicon glyphicon-pause');
		$("#pause").attr('onclick', "pause()");
	} else {
		$("#pause-text").text(" Resume ");
		$("#pause-icon").attr('class', 'glyphicon glyphicon-play');
		$("#pause").attr('onclick', "resume()");
	}
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

function showSpinner() {
	for (let i = 0; i < arguments.length; i++) {
		arguments[i].poster = './img/transparent-1px.png';
		arguments[i].style.background = "center transparent url('./img/spinner.gif') no-repeat";
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

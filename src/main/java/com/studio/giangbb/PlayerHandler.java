/*
 * (C) Copyright 2015 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.studio.giangbb;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.kurento.client.*;
import org.kurento.commons.exception.KurentoException;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Protocol handler for video player through WebRTC.
 *
 * @author Giangbb
 *
 */
public class PlayerHandler extends TextWebSocketHandler {
  private final Logger log = LoggerFactory.getLogger(PlayerHandler.class);

  @Autowired
  private KurentoClient kurento;

  @Autowired
  private UserRepo userRepo;

  private final Gson gson = new GsonBuilder().create();

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
    String sessionId = session.getId();
    log.debug("Incoming message {} from sessionId", jsonMessage, sessionId);

    try {
      switch (jsonMessage.get("id").getAsString()) {
        case "start":
          start(session, jsonMessage);
          break;
        case "stop":
          stop(sessionId);
          break;
        case "pause":
          pause(sessionId);
          break;
        case "resume":
          resume(session);
          break;
        case "debugDot":
          debugDot(session);
          break;
        case "doSeek":
          doSeek(session, jsonMessage);
          break;
        case "getPosition":
          getPosition(session);
          break;
        case "onIceCandidate":
          onIceCandidate(sessionId, jsonMessage);
          break;
        default:
          sendError(session, "Invalid message with id " + jsonMessage.get("id").getAsString());
          break;
      }
    } catch (Throwable t) {
      log.error("Exception handling message {} in sessionId {}", jsonMessage, sessionId, t);
      sendError(session, t.getMessage());
    }
  }

  private void start(final WebSocketSession session, JsonObject jsonMessage) throws IOException{
    if (!jsonMessage.has("videourl")){
      JsonObject response = new JsonObject();
      response.addProperty("id", "viewerResponse");
      response.addProperty("response", "rejected");
      response.addProperty("message", "No videourl ...");
      session.sendMessage(new TextMessage(response.toString()));
      return;
    }

    String videourl = jsonMessage.get("videourl").getAsString();

    // 1. Media pipeline
    final UserSession user = new UserSession(session, UserSession.Role.PRESENTER,videourl);
    MediaPipeline pipeline = kurento.createMediaPipeline();
    user.setMediaPipeline(pipeline);
    WebRtcEndpoint webRtcEndpoint = new WebRtcEndpoint.Builder(pipeline).build();
    user.setWebRtcEndpoint(webRtcEndpoint);

    final PlayerEndpoint playerEndpoint = new PlayerEndpoint.Builder(pipeline, videourl).build();
    user.setPlayerEndpoint(playerEndpoint);
    userRepo.putUserSession(session.getId(), user);

    user.connectToPlayerEndpoint(webRtcEndpoint);


    // 2. WebRtcEndpoint
    // ICE candidates
    webRtcEndpoint.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

      @Override
      public void onEvent(IceCandidateFoundEvent event) {
        JsonObject response = new JsonObject();
        response.addProperty("id", "iceCandidate");
        response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
        try {
          synchronized (session) {
            session.sendMessage(new TextMessage(response.toString()));
          }
        } catch (IOException e) {
          log.debug(e.getMessage());
        }
      }
    });

    // Continue the SDP Negotiation: Generate an SDP Answer
    String sdpOffer = jsonMessage.get("sdpOffer").getAsString();
    String sdpAnswer = webRtcEndpoint.processOffer(sdpOffer);

//    log.info("[Handler::start] SDP Offer from browser to KMS:\n{}", sdpOffer);
//    log.info("[Handler::start] SDP Answer from KMS to browser:\n{}", sdpAnswer);

    JsonObject response = new JsonObject();
    response.addProperty("id", "startResponse");
    response.addProperty("response", "accepted");
    response.addProperty("sdpAnswer", sdpAnswer);
    user.sendMessage(response);

    webRtcEndpoint.addMediaStateChangedListener(new EventListener<MediaStateChangedEvent>() {
      @Override
      public void onEvent(MediaStateChangedEvent event) {

        if (event.getNewState() == MediaState.CONNECTED) {
          VideoInfo videoInfo = playerEndpoint.getVideoInfo();

          JsonObject response = new JsonObject();
          response.addProperty("id", "videoInfo");
          response.addProperty("isSeekable", videoInfo.getIsSeekable());
          response.addProperty("initSeekable", videoInfo.getSeekableInit());
          response.addProperty("endSeekable", videoInfo.getSeekableEnd());
          response.addProperty("videoDuration", videoInfo.getDuration());
          user.sendMessage(response);
        }
      }
    });

    webRtcEndpoint.gatherCandidates();

    // 3. PlayEndpoint
    playerEndpoint.addErrorListener(new EventListener<ErrorEvent>() {
      @Override
      public void onEvent(ErrorEvent event) {
        log.info("ErrorEvent: {}", event.getDescription());
        sendPlayEnd(session);
      }
    });

    playerEndpoint.addEndOfStreamListener(new EventListener<EndOfStreamEvent>() {
      @Override
      public void onEvent(EndOfStreamEvent event) {
        log.info("EndOfStreamEvent: {}", event.getTimestampMillis());
        onStreamEnd(session);
      }
    });

    playerEndpoint.play();
  }

  private void pause(String sessionId) {
    UserSession user = userRepo.getPresenterUserSession(sessionId);

    if (user != null) {
      user.getPlayerEndpoint().pause();
    }
  }

  private void resume(final WebSocketSession session) {
    UserSession user = userRepo.getPresenterUserSession(session.getId());

    if (user != null) {
      user.getPlayerEndpoint().play();
      VideoInfo videoInfo = user.getPlayerEndpoint().getVideoInfo();

      JsonObject response = new JsonObject();
      response.addProperty("id", "videoInfo");
      response.addProperty("isSeekable", videoInfo.getIsSeekable());
      response.addProperty("initSeekable", videoInfo.getSeekableInit());
      response.addProperty("endSeekable", videoInfo.getSeekableEnd());
      response.addProperty("videoDuration", videoInfo.getDuration());
      user.sendMessage(response);
    }
  }

  private void stop(String sessionId) {
    UserSession presenter = userRepo.getPresenterUserSession(sessionId);
    if (presenter != null) {
      stopViewers(presenter.getVideoUrl());
      presenter.release();
      userRepo.removeUserSession(sessionId);
    }
  }

  public void stopViewers(String videoUrl){
    List<UserSession> viewers = userRepo.getAllViewersUserSession(videoUrl);
    viewers.forEach(viewer -> {
      JsonObject response = new JsonObject();
      response.addProperty("id", "stopCommunication");
      viewer.sendMessage(response);
      userRepo.removeUserSession(viewer.getSession().getId());
    });
  }

  private void debugDot(final WebSocketSession session) {
    UserSession user = userRepo.getPresenterUserSession(session.getId());

    if (user != null) {
      final String pipelineDot = user.getMediaPipeline().getGstreamerDot();
      try (PrintWriter out = new PrintWriter("player.dot")) {
        out.println(pipelineDot);
      } catch (IOException ex) {
        log.error("[Handler::debugDot] Exception: {}", ex.getMessage());
      }
      final String playerDot = user.getPlayerEndpoint().getElementGstreamerDot();
      try (PrintWriter out = new PrintWriter("player-decoder.dot")) {
        out.println(playerDot);
      } catch (IOException ex) {
        log.error("[Handler::debugDot] Exception: {}", ex.getMessage());
      }
    }

    ServerManager sm = kurento.getServerManager();
    log.warn("[Handler::debugDot] CPU COUNT: {}", sm.getCpuCount());
    log.warn("[Handler::debugDot] CPU USAGE: {}", sm.getUsedCpu(1000));
    log.warn("[Handler::debugDot] RAM USAGE: {}", sm.getUsedMemory());
  }

  private void doSeek(final WebSocketSession session, JsonObject jsonMessage) {
    UserSession user = userRepo.getPresenterUserSession(session.getId());

    if (user != null) {
      try {
        user.getPlayerEndpoint().setPosition(jsonMessage.get("position").getAsLong());
      } catch (KurentoException e) {
        log.debug("The seek cannot be performed");
        JsonObject response = new JsonObject();
        response.addProperty("id", "seek");
        response.addProperty("message", "Seek failed");
        user.sendMessage(response);
      }
    }
  }

  private void getPosition(final WebSocketSession session) {
    UserSession user = userRepo.getPresenterUserSession(session.getId());

    if (user != null) {
      long position = user.getPlayerEndpoint().getPosition();

      JsonObject response = new JsonObject();
      response.addProperty("id", "position");
      response.addProperty("position", position);
      user.sendMessage(response);
    }
  }

  private void onIceCandidate(String sessionId, JsonObject jsonMessage) {
    UserSession user = userRepo.getPresenterUserSession(sessionId);

    if (user != null) {
      JsonObject jsonCandidate = jsonMessage.get("candidate").getAsJsonObject();
      IceCandidate candidate =
          new IceCandidate(jsonCandidate.get("candidate").getAsString(), jsonCandidate
              .get("sdpMid").getAsString(), jsonCandidate.get("sdpMLineIndex").getAsInt());
      user.getWebRtcEndpoint().addIceCandidate(candidate);
    }
  }


  public void onStreamEnd(WebSocketSession session) {
    UserSession user = userRepo.getPresenterUserSession(session.getId());
    if (user != null) {
      user.getPlayerEndpoint().play();
    }
  }



  public void sendPlayEnd(WebSocketSession session) {
    UserSession user = userRepo.getPresenterUserSession(session.getId());
    if (user != null) {
      JsonObject response = new JsonObject();
      response.addProperty("id", "playEnd");
      user.sendMessage(response);

      stop(session.getId());
    }
  }

  private void sendError(WebSocketSession session, String message) {
    UserSession user = userRepo.getPresenterUserSession(session.getId());

    if (user != null) {
      JsonObject response = new JsonObject();
      response.addProperty("id", "error");
      response.addProperty("message", message);
      user.sendMessage(response);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    stop(session.getId());
  }
}

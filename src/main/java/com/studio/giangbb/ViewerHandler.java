/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
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
import org.apache.commons.lang3.StringUtils;
import org.kurento.client.*;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protocol handler for 1 to N video call communication.
 *
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 5.0.0
 */
public class ViewerHandler extends TextWebSocketHandler {
  private static final Logger log = LoggerFactory.getLogger(ViewerHandler.class);

  @Autowired
  private KurentoClient kurento;

  @Autowired
  private UserRepo userRepo;

  private static final Gson gson = new GsonBuilder().create();



  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
    log.debug("Incoming message from session '{}': {}", session.getId(), jsonMessage);

    switch (jsonMessage.get("id").getAsString()) {
      case "viewer":
        try {
          viewer(session, jsonMessage);
        } catch (Throwable t) {
          handleErrorResponse(t, session, "viewerResponse");
        }
        break;
      case "onIceCandidate": {
        JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();

        UserSession user = userRepo.getUserSession(session.getId());
        if (user != null) {
          IceCandidate cand =
                  new IceCandidate(candidate.get("candidate").getAsString(), candidate.get("sdpMid")
                          .getAsString(), candidate.get("sdpMLineIndex").getAsInt());
          user.addCandidate(cand);
        }
        break;
      }
      case "stop":
        stop(session);
        break;
      default:
        break;
    }
  }

  private void handleErrorResponse(Throwable throwable, WebSocketSession session, String responseId)
          throws IOException {
    stop(session);
    log.error(throwable.getMessage(), throwable);
    JsonObject response = new JsonObject();
    response.addProperty("id", responseId);
    response.addProperty("response", "rejected");
    response.addProperty("message", throwable.getMessage());
    session.sendMessage(new TextMessage(response.toString()));
  }

  private synchronized void viewer(final WebSocketSession session, JsonObject jsonMessage)
          throws IOException {
    if (!jsonMessage.has("videourl")){
      JsonObject response = new JsonObject();
      response.addProperty("id", "viewerResponse");
      response.addProperty("response", "rejected");
      response.addProperty("message", "No videourl ...");
      session.sendMessage(new TextMessage(response.toString()));
      return;
    }

    String videourl = jsonMessage.get("videourl").getAsString();
    UserSession presenter = userRepo.findPresenterUserSessionByVideoUrl(videourl);

    if (presenter == null) {
      JsonObject response = new JsonObject();
      response.addProperty("id", "viewerResponse");
      response.addProperty("response", "rejected");
      response.addProperty("message",
              "No active sender now. Become sender or . Try again later ...");
      session.sendMessage(new TextMessage(response.toString()));
      return;
    }


    if (userRepo.getViewerUserSession(session.getId()) != null) {
      JsonObject response = new JsonObject();
      response.addProperty("id", "viewerResponse");
      response.addProperty("response", "rejected");
      response.addProperty("message", "You are already viewing in this session. "
              + "Use a different browser to add additional viewers.");
      session.sendMessage(new TextMessage(response.toString()));
      return;
    }

    UserSession viewer = new UserSession(session, UserSession.Role.VIEWER, videourl);
    userRepo.putUserSession(session.getId(), viewer);

    WebRtcEndpoint nextWebRtc = new WebRtcEndpoint.Builder(presenter.getMediaPipeline()).build();

    nextWebRtc.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {
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

    viewer.setWebRtcEndpoint(nextWebRtc);
    presenter.connectToPlayerEndpoint(nextWebRtc);

    String sdpOffer = jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString();
    String sdpAnswer = nextWebRtc.processOffer(sdpOffer);

    JsonObject response = new JsonObject();
    response.addProperty("id", "viewerResponse");
    response.addProperty("response", "accepted");
    response.addProperty("sdpAnswer", sdpAnswer);

    synchronized (session) {
      viewer.sendMessage(response);
    }
    nextWebRtc.gatherCandidates();
  }

  private synchronized void stop(WebSocketSession session) throws IOException {
    String sessionId = session.getId();
    UserSession viewer = userRepo.getViewerUserSession(sessionId);
    if (viewer != null){
      if(viewer.getWebRtcEndpoint() != null){
        viewer.getWebRtcEndpoint().release();
      }
      userRepo.removeUserSession(sessionId);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    stop(session);
  }

}

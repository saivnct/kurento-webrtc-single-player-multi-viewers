/*
 * (C) Copyright 2016 Kurento (http://kurento.org/)
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

import com.google.gson.JsonObject;
import org.kurento.client.IceCandidate;
import org.kurento.client.MediaPipeline;
import org.kurento.client.PlayerEndpoint;
import org.kurento.client.WebRtcEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * Protocol handler for video player through WebRTC.
 *
 * @author Giangbb
 *
 */
public class UserSession {
  private static final Logger log = LoggerFactory.getLogger(UserSession.class);

  public enum Role {
    PRESENTER,
    VIEWER,
  }

  private final Role role;
  private final WebSocketSession session;

  private final String videoUrl;
  private WebRtcEndpoint webRtcEndpoint;



  //region only presenter
  private MediaPipeline mediaPipeline;
  private PlayerEndpoint playerEndpoint;
  //endregion

  public UserSession(WebSocketSession session, Role role, String videoUrl) {
    this.videoUrl = videoUrl;
    this.role = role;
    this.session = session;
  }

  public Role getRole() {
    return role;
  }

  public WebSocketSession getSession() {
    return session;
  }

  public String getVideoUrl() {
    return videoUrl;
  }

  public WebRtcEndpoint getWebRtcEndpoint() {
    return webRtcEndpoint;
  }

  public void setWebRtcEndpoint(WebRtcEndpoint webRtcEndpoint) {
    this.webRtcEndpoint = webRtcEndpoint;
  }

  public MediaPipeline getMediaPipeline() {
    return mediaPipeline;
  }

  public void setMediaPipeline(MediaPipeline mediaPipeline) {
    this.mediaPipeline = mediaPipeline;
  }

  public void addCandidate(IceCandidate candidate) {
    webRtcEndpoint.addIceCandidate(candidate);
  }

  public PlayerEndpoint getPlayerEndpoint() {
    return playerEndpoint;
  }

  public void setPlayerEndpoint(PlayerEndpoint playerEndpoint) {
    this.playerEndpoint = playerEndpoint;
  }

  public void connectToPlayerEndpoint(WebRtcEndpoint webRtcEndpoint) {
    this.playerEndpoint.connect(webRtcEndpoint);
  }

  public void release() {
    try{
      this.playerEndpoint.stop();
    }catch (Exception e){
      log.error(e.getMessage());
    }

    try{
      this.mediaPipeline.release();
    }catch (Exception e){
      log.error(e.getMessage());
    }

    try{
      this.webRtcEndpoint.release();
    }catch (Exception e){
      log.error(e.getMessage());
    }



  }


  public void sendMessage(JsonObject message) {
    try {
      log.debug("Sending message from user with session Id '{}': {}", session.getId(), message);
      session.sendMessage(new TextMessage(message.toString()));
    } catch (Exception e) {
      log.error("Exception sending message", e);
    }
  }
}

package com.studio.giangbb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by giangbb on 11/05/2023
 */
public class UserRepo {
    private final ConcurrentHashMap<String, UserSession> users = new ConcurrentHashMap<>();

    public ConcurrentHashMap<String, UserSession> getUsers() {
        return users;
    }

    public void putUserSession(String sessionId, UserSession user) {
        users.put(sessionId, user);
    }

    public UserSession getUserSession(String sessionId) {
        return users.get(sessionId);
    }

    public UserSession removeUserSession(String sessionId) {
        return users.remove(sessionId);
    }

    public UserSession getPresenterUserSession(String sessionId) {
        UserSession userSession =  getUserSession(sessionId);
        if (userSession != null && userSession.getRole() == UserSession.Role.PRESENTER) {
            return userSession;
        }
        return null;
    }

    public UserSession getViewerUserSession(String sessionId){
        UserSession userSession =  getUserSession(sessionId);
        if (userSession != null && userSession.getRole() == UserSession.Role.VIEWER) {
            return userSession;
        }
        return null;
    }


    public List<UserSession> getAllViewersUserSession(String videoUrl){
        List<UserSession> viewers = new ArrayList<>();
        for (Map.Entry<String, UserSession> entry : users.entrySet()) {
            UserSession user = entry.getValue();
            if (user.getRole() == UserSession.Role.VIEWER &&
                    user.getVideoUrl().equals(videoUrl)) {
                viewers.add(user);
            }
        }
        return viewers;
    }

    public UserSession findPresenterUserSessionByVideoUrl(final String videourl) {
        return users.values().stream()
                .filter(userSession -> userSession.getRole() == UserSession.Role.PRESENTER && userSession.getVideoUrl().equals(videourl))
                .findFirst()
                .orElse(null);
    }
}

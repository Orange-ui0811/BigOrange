package com.orangejd.server.websocket;

import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/ws/{clientId}")
public class WebSocketServer {
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("clientId") String clientId) {
        SESSIONS.put(clientId, session);
    }

    @OnClose
    public void onClose(@PathParam("clientId") String clientId) {
        SESSIONS.remove(clientId);
    }

    public void sendToAll(String message) {
        for (Session session : SESSIONS.values()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (Exception ignored) {
            }
        }
    }
}


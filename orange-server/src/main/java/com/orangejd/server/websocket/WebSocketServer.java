package com.orangejd.server.websocket;

import com.orangejd.server.service.WebSocketTicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/ws/{userId}")
public class WebSocketServer {
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static WebSocketTicketService ticketService;

    @Autowired
    public void setTicketService(WebSocketTicketService service) { ticketService = service; }

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        String ticket = firstParameter(session, "ticket");
        if (ticketService != null && ticketService.consume(ticket, userId)) {
            SESSIONS.put(userId, session);
            return;
        }
        closeQuietly(session);
    }

    @OnClose
    public void onClose(@PathParam("userId") String userId) {
        SESSIONS.remove(userId);
    }

    public static void sendToUser(String userId, String message) {
        Session session = SESSIONS.get(userId);
        if (session != null) {
            try { session.getBasicRemote().sendText(message); } catch (IOException ignored) {}
        }
    }

    private static void closeQuietly(Session session) {
        try { session.close(); } catch (IOException ignored) {}
    }

    private static String firstParameter(Session session, String name) {
        return session.getRequestParameterMap().get(name) == null
                ? null : session.getRequestParameterMap().get(name).get(0);
    }
}

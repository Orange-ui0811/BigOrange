package com.orangejd.server.websocket;

import com.orangejd.common.utils.JwtUtil;
import com.orangejd.common.properties.JwtProperties;
import io.jsonwebtoken.Claims;
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
    private static JwtProperties jwtProperties;

    @Autowired
    public void setJwtProperties(JwtProperties props) { jwtProperties = props; }

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        // token 鉴权：从 URL 参数取
        String token = session.getRequestParameterMap().get("token") != null
                ? session.getRequestParameterMap().get("token").get(0) : null;
        if (token == null) { try { session.close(); } catch (IOException e) {} return; }
        try {
            Claims claims = JwtUtil.parseJwt(jwtProperties.getAppSecretKey(), token);
            String id = claims.get("id").toString();
            if (!id.equals(userId)) { try { session.close(); } catch (IOException e) {} return; }
            SESSIONS.put(userId, session);
        } catch (Exception e) { try { session.close(); } catch (IOException e2) {} }
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
}


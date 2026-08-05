package com.orangejd.server.websocket;

import jakarta.websocket.server.ServerEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketServerRegistrationTest {

    @Test
    void endpointIsDiscoverableBySpringAndTheEmbeddedWebSocketContainer() {
        assertTrue(WebSocketServer.class.isAnnotationPresent(Component.class));
        ServerEndpoint endpoint = WebSocketServer.class.getAnnotation(ServerEndpoint.class);
        assertEquals("/ws/{userId}", endpoint.value());
    }
}

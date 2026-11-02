package com.orangejd.server.websocket;

import com.orangejd.server.service.WebSocketTicketService;
import org.springframework.stereotype.Component;

/** Bridges Spring-managed authentication into endpoint instances created by the WebSocket container. */
@Component
final class WebSocketTicketBridge {
    WebSocketTicketBridge(WebSocketTicketService ticketService) {
        WebSocketServer.installTicketService(ticketService);
    }
}

package com.thelastwar.wsgateway;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.AttributeKey;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Netty handler for WebSocket connections.
 * 
 * Handles:
 * - Connection establishment and termination
 * - Ping/pong heartbeat mechanism
 * - Inbound message routing to WebSocketGateway
 * - Protocol upgrade from HTTP to WebSocket
 */
public class WebSocketServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private static final Logger LOGGER = Logger.getLogger(WebSocketServerHandler.class.getName());
    private static final AttributeKey<WebSocketSession> SESSION_KEY = AttributeKey.valueOf("session");

    private final WebSocketGateway gateway;
    private final WebSocketMessageSerializer serializer;

    public WebSocketServerHandler(WebSocketGateway gateway, TransportFormat format) {
        this.gateway = gateway;
        this.serializer = createSerializer(format);
    }

    private WebSocketMessageSerializer createSerializer(TransportFormat format) {
        return switch (format) {
            case JSON -> new JsonWebSocketSerializer();
            case MESSAGEPACK -> new MessagePackWebSocketSerializer();
        };
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        WebSocketSession session = new WebSocketSession(sessionId, ctx, serializer.getFormat());
        ctx.channel().attr(SESSION_KEY).set(session);

        gateway.registerSession(session);
        LOGGER.log(Level.FINE, "WebSocket connection established: {0}", sessionId);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        WebSocketSession session = ctx.channel().attr(SESSION_KEY).get();
        if (session != null) {
            gateway.unregisterSession(session);
            LOGGER.log(Level.FINE, "WebSocket connection closed: {0}", session.getSessionId());
        }
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        WebSocketSession session = ctx.channel().attr(SESSION_KEY).get();
        if (session == null) {
            LOGGER.warning("Received frame for unknown session");
            ctx.close();
            return;
        }

        session.recordReceivedMessage();

        if (frame instanceof PingWebSocketFrame ping) {
            handlePing(ctx, ping);
        } else if (frame instanceof PongWebSocketFrame) {
            handlePong(session);
        } else if (frame instanceof TextWebSocketFrame text) {
            handleTextMessage(session, text);
        } else if (frame instanceof BinaryWebSocketFrame binary) {
            handleBinaryMessage(session, binary);
        } else if (frame instanceof CloseWebSocketFrame) {
            ctx.close();
        } else {
            LOGGER.log(Level.WARNING, "Unsupported WebSocket frame type: {0}", frame.getClass().getName());
        }
    }

    private void handlePing(ChannelHandlerContext ctx, PingWebSocketFrame frame) {
        ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
    }

    private void handlePong(WebSocketSession session) {
        session.updateHeartbeat();
    }

    private void handleTextMessage(WebSocketSession session, TextWebSocketFrame frame) {
        try {
            String text = frame.text();
            byte[] data = text.getBytes();
            WebSocketMessage message = serializer.deserialize(data);
            gateway.handleClientMessage(session, message);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to process text message", e);
            sendError(session, "Invalid message format");
        }
    }

    private void handleBinaryMessage(WebSocketSession session, BinaryWebSocketFrame frame) {
        try {
            byte[] data = new byte[frame.content().readableBytes()];
            frame.content().readBytes(data);
            WebSocketMessage message = serializer.deserialize(data);
            gateway.handleClientMessage(session, message);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to process binary message", e);
            sendError(session, "Invalid message format");
        }
    }

    private void sendError(WebSocketSession session, String errorMessage) {
        try {
            // Create a simple error response
            String errorJson = "{\"type\":\"ERROR\",\"payload\":\"" + errorMessage + "\"}";
            TextWebSocketFrame frame = new TextWebSocketFrame(errorJson);
            session.sendMessage(frame);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send error message", e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.log(Level.WARNING, "WebSocket handler exception", cause);
        ctx.close();
    }
}

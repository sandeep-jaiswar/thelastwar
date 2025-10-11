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
        LOGGER.fine("WebSocket connection established: " + sessionId);
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        WebSocketSession session = ctx.channel().attr(SESSION_KEY).get();
        if (session != null) {
            gateway.unregisterSession(session);
            LOGGER.fine("WebSocket connection closed: " + session.getSessionId());
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
        
        if (frame instanceof PingWebSocketFrame) {
            handlePing(ctx, (PingWebSocketFrame) frame);
        } else if (frame instanceof PongWebSocketFrame) {
            handlePong(session);
        } else if (frame instanceof TextWebSocketFrame) {
            handleTextMessage(session, (TextWebSocketFrame) frame);
        } else if (frame instanceof BinaryWebSocketFrame) {
            handleBinaryMessage(session, (BinaryWebSocketFrame) frame);
        } else if (frame instanceof CloseWebSocketFrame) {
            ctx.close();
        } else {
            LOGGER.warning("Unsupported WebSocket frame type: " + frame.getClass().getName());
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
            WebSocketMessage error = WebSocketMessage.error(errorMessage);
            byte[] data = serializer.serialize(null); // Simplified
            TextWebSocketFrame frame = new TextWebSocketFrame(new String(data));
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

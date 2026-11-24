package com.example.EPustok.chat;

import com.example.EPustok.identity.JwtService;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Keeps the JWT id (jti) and expiry of the handshake token in the WebSocket session attributes. The STOMP guard
 * reads them to reject frames once the token is revoked (logout) or expired. The principal itself is still set
 * from the authenticated HTTP request during the handshake.
 */
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {
    public static final String JTI_ATTRIBUTE = "chat.jti";
    public static final String EXPIRES_AT_ATTRIBUTE = "chat.expiresAt";

    private final JwtService tokens;

    public ChatHandshakeInterceptor(JwtService tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        String token = tokens.extract(servletRequest.getServletRequest());
        if (token == null) {
            return false;
        }
        try {
            Jwt jwt = tokens.validate(token);
            attributes.put(JTI_ATTRIBUTE, jwt.getId());
            attributes.put(EXPIRES_AT_ATTRIBUTE, jwt.getExpiresAt());
            return true;
        } catch (JwtException ex) {
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // Nothing to do after the handshake.
    }
}

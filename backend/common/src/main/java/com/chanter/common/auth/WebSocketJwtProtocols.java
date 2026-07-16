package com.chanter.common.auth;

import java.util.List;

/**
 * Extracts a JWT from {@code Sec-WebSocket-Protocol} values (SEC-11).
 *
 * <p>Accepted forms:
 * <ul>
 *   <li>{@code chanter-jwt, &lt;token&gt;} (two subprotocols)</li>
 *   <li>{@code chanter-jwt.&lt;token&gt;} (single subprotocol)</li>
 * </ul>
 */
public final class WebSocketJwtProtocols {

    public static final String PROTOCOL_NAME = "chanter-jwt";

    private WebSocketJwtProtocols() {
    }

    public static String extractToken(List<String> protocolHeaderValues) {
        if (protocolHeaderValues == null || protocolHeaderValues.isEmpty()) {
            return null;
        }
        for (String header : protocolHeaderValues) {
            if (header == null) {
                continue;
            }
            for (String part : header.split(",")) {
                String trimmed = part.trim();
                if (trimmed.startsWith(PROTOCOL_NAME + ".")) {
                    String token = trimmed.substring(PROTOCOL_NAME.length() + 1).trim();
                    if (!token.isBlank()) {
                        return token;
                    }
                }
            }
        }
        for (String header : protocolHeaderValues) {
            if (header == null) {
                continue;
            }
            String[] parts = header.split(",");
            for (int i = 0; i < parts.length - 1; i++) {
                if (PROTOCOL_NAME.equals(parts[i].trim())) {
                    String candidate = parts[i + 1].trim();
                    if (!candidate.isBlank()) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }
}

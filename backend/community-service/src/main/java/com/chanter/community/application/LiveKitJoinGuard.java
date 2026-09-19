package com.chanter.community.application;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.chanter.community.config.LiveKitProperties;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Verifies signaling authority without returning user data or accepting an edge service credential. */
@Service
public class LiveKitJoinGuard {
    private final JWTVerifier verifier;
    private final LiveMediaAccess access;

    public LiveKitJoinGuard(LiveKitProperties properties, LiveMediaAccess access) {
        this.verifier = JWT.require(Algorithm.HMAC256(properties.apiSecret())).withIssuer(properties.apiKey()).build();
        this.access = access;
    }

    public void requireJoin(String token) {
        if (token == null || token.length() > 8192) throw invalid();
        String room;
        UUID user;
        try {
            var claims = verifier.verify(token);
            Instant expiry = claims.getExpiresAtAsInstant();
            Instant notBefore = claims.getNotBeforeAsInstant();
            if (expiry == null || notBefore == null || !expiry.isAfter(Instant.now())) throw invalid();
            Map<String, Object> video = claims.getClaim("video").asMap();
            if (video == null || !Boolean.TRUE.equals(video.get("roomJoin"))) throw invalid();
            for (String privilege : new String[]{"roomAdmin", "roomCreate", "roomList", "roomRecord", "ingressAdmin"}) {
                if (Boolean.TRUE.equals(video.get(privilege))) throw invalid();
            }
            room = (String) video.get("room");
            if ("__chanter_release_health".equals(room)) {
                String subject = claims.getSubject();
                if (subject == null || !subject.startsWith("release-health-")) throw invalid();
                UUID.fromString(subject.substring("release-health-".length()));
                if (!Boolean.FALSE.equals(video.get("canPublish")) || !Boolean.FALSE.equals(video.get("canSubscribe"))
                        || !Boolean.FALSE.equals(video.get("canPublishData"))
                        || expiry.isAfter(Instant.now().plusSeconds(60))
                        || expiry.isAfter(notBefore.plusSeconds(75))) throw invalid();
                return;
            }
            if (claims.getSubject() == null) throw invalid();
            user = UUID.fromString(claims.getSubject());
            if (room == null || room.length() > 64) throw invalid();
        } catch (JWTVerificationException | IllegalArgumentException | ClassCastException exception) {
            throw invalid();
        }
        access.requireAllowed(room, user);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Media join authorization required");
    }
}

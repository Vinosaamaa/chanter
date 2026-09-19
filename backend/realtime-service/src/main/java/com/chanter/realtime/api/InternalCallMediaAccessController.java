package com.chanter.realtime.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.realtime.websocket.DirectMessageCallHub;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@RestController
public class InternalCallMediaAccessController {
    private final DirectMessageCallHub calls;
    private final byte[] token;
    public InternalCallMediaAccessController(DirectMessageCallHub calls,
            @Value("${chanter.internal-service-token}") String token) {
        this.calls = calls;
        this.token = InternalServiceTokens.requireBytes(token);
    }

    @GetMapping("/internal/v1/dm-calls/{id}/media-access")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> requireAccess(@PathVariable UUID id, @RequestParam UUID userId,
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String presented) {
        if (!MessageDigest.isEqual(token, (presented == null ? "" : presented).getBytes(StandardCharsets.UTF_8))) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal service authentication required"));
        }
        return calls.requireActiveMediaAccess(userId, id);
    }
}

package com.chanter.auth.moderation;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InternalModerationController {
    private final byte[] token;
    private final ModerationRestrictions restrictions;
    public InternalModerationController(@Value("${chanter.internal-service-token}") String token, ModerationRestrictions restrictions) {
        this.token = InternalServiceTokens.requireBytes(token);
        this.restrictions = restrictions;
    }

    @PostMapping("/internal/v1/moderation/access")
    Map<String, Boolean> access(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String presented,
            @Valid @RequestBody AccessRequest request) {
        if (!MessageDigest.isEqual(token, (presented == null ? "" : presented).getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal service authentication required");
        if (request.userId() == null && request.targets().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An account or source is required");
        if (request.userId() != null) restrictions.requireActiveAccount(request.userId());
        Instant now = Instant.now();
        for (Target target : request.targets()) {
            if (restrictions.isRestricted(target.type(), target.id(), now))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Source is restricted by moderation");
        }
        return Map.of("allowed", true);
    }

    record AccessRequest(UUID userId, @NotNull @Size(max=100) List<@Valid Target> targets) { }
    record Target(@NotBlank String type, @NotNull UUID id) { }
}

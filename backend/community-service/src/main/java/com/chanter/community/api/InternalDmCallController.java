package com.chanter.community.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.community.application.LiveKitTokenIssuer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/dm-calls")
public class InternalDmCallController {

    public static final String CALLER_USER_ID_HEADER = "X-Dm-Call-Caller-Id";
    public static final String CALLEE_USER_ID_HEADER = "X-Dm-Call-Callee-Id";

    private final LiveKitTokenIssuer liveKitTokenIssuer;
    private final byte[] internalServiceToken;

    public InternalDmCallController(
            LiveKitTokenIssuer liveKitTokenIssuer,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
        this.liveKitTokenIssuer = liveKitTokenIssuer;
        this.internalServiceToken = internalServiceToken.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/{callId}/media-token")
    public VoiceMediaTokenResponse issueMediaToken(
            @PathVariable UUID callId,
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String serviceToken,
            @RequestHeader(AuthHeaders.USER_ID) UUID participantUserId,
            @RequestHeader(CALLER_USER_ID_HEADER) UUID callerUserId,
            @RequestHeader(CALLEE_USER_ID_HEADER) UUID calleeUserId
    ) {
        requireInternalService(serviceToken);
        requireParticipant(participantUserId, callerUserId, calleeUserId);
        return VoiceMediaTokenResponse.from(liveKitTokenIssuer.issueForDmCall(callId, participantUserId));
    }

    private void requireInternalService(String presentedToken) {
        byte[] presented = presentedToken == null
                ? new byte[0]
                : presentedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(internalServiceToken, presented)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal service authentication required");
        }
    }

    private static void requireParticipant(UUID participantUserId, UUID callerUserId, UUID calleeUserId) {
        if (!participantUserId.equals(callerUserId) && !participantUserId.equals(calleeUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a participant in this call");
        }
    }
}

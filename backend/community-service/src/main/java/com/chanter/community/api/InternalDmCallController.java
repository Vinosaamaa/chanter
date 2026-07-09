package com.chanter.community.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.community.application.LiveKitTokenIssuer;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/dm-calls")
public class InternalDmCallController {

    private final LiveKitTokenIssuer liveKitTokenIssuer;

    public InternalDmCallController(LiveKitTokenIssuer liveKitTokenIssuer) {
        this.liveKitTokenIssuer = liveKitTokenIssuer;
    }

    @PostMapping("/{callId}/media-token")
    public VoiceMediaTokenResponse issueMediaToken(
            @PathVariable UUID callId,
            @RequestHeader(AuthHeaders.USER_ID) UUID participantUserId
    ) {
        return VoiceMediaTokenResponse.from(liveKitTokenIssuer.issueForDmCall(callId, participantUserId));
    }
}

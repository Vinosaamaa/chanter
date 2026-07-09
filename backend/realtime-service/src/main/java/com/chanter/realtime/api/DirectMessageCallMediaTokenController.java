package com.chanter.realtime.api;

import com.chanter.realtime.application.DmCallMediaToken;
import com.chanter.realtime.websocket.DirectMessageCallHub;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/direct-message-calls")
public class DirectMessageCallMediaTokenController {

    private final DirectMessageCallHub directMessageCallHub;

    public DirectMessageCallMediaTokenController(DirectMessageCallHub directMessageCallHub) {
        this.directMessageCallHub = directMessageCallHub;
    }

    @PostMapping("/{callId}/media-token")
    public DmCallMediaTokenResponse issueMediaToken(
            @PathVariable UUID callId,
            @RequestAttribute(name = "authenticatedUserId") UUID userId
    ) {
        return DmCallMediaTokenResponse.from(directMessageCallHub.issueMediaToken(userId, callId));
    }

    public record DmCallMediaTokenResponse(
            String roomName,
            String serverUrl,
            String participantToken,
            boolean canSpeak,
            boolean canListen
    ) {
        static DmCallMediaTokenResponse from(DmCallMediaToken token) {
            return new DmCallMediaTokenResponse(
                    token.roomName(),
                    token.serverUrl(),
                    token.participantToken(),
                    token.canSpeak(),
                    token.canListen()
            );
        }
    }
}

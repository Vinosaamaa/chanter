package com.chanter.community.api;

import com.chanter.community.domain.VoicePresence;
import java.util.UUID;

public record VoicePresenceResponse(
        UUID channelId,
        UUID memberUserId,
        boolean canSpeak,
        boolean canListen
) {
    static VoicePresenceResponse from(VoicePresence presence) {
        return new VoicePresenceResponse(
                presence.channelId(),
                presence.memberUserId(),
                presence.canSpeak(),
                presence.canListen()
        );
    }
}

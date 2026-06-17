package com.chanter.community.api;

import com.chanter.community.domain.VoicePresence;
import java.util.List;

public record VoicePresenceListResponse(List<VoicePresenceResponse> presences) {
    static VoicePresenceListResponse from(List<VoicePresence> presences) {
        return new VoicePresenceListResponse(
                presences.stream()
                        .map(VoicePresenceResponse::from)
                        .toList()
        );
    }
}

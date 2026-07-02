package com.chanter.community.api;

import com.chanter.community.domain.VoiceMediaToken;

public record VoiceMediaTokenResponse(
        String roomName,
        String serverUrl,
        String participantToken,
        boolean canSpeak,
        boolean canListen
) {
    public static VoiceMediaTokenResponse from(VoiceMediaToken token) {
        return new VoiceMediaTokenResponse(
                token.roomName(),
                token.serverUrl(),
                token.participantToken(),
                token.canSpeak(),
                token.canListen()
        );
    }
}

package com.chanter.community.domain;

public record VoiceMediaToken(
        String roomName,
        String serverUrl,
        String participantToken,
        boolean canSpeak,
        boolean canListen
) {
}

package com.chanter.realtime.application;

public record DmCallMediaToken(
        String roomName,
        String serverUrl,
        String participantToken,
        boolean canSpeak,
        boolean canListen
) {
}

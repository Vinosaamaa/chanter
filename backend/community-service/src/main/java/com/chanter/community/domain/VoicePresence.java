package com.chanter.community.domain;

import java.util.UUID;

public record VoicePresence(
        UUID channelId,
        UUID memberUserId,
        boolean canSpeak,
        boolean canListen
) {
}

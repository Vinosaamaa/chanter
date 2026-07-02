package com.chanter.community.application;

import com.chanter.community.config.LiveKitProperties;
import com.chanter.community.domain.VoiceMediaToken;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LiveKitTokenIssuer {

    private final LiveKitProperties properties;

    public LiveKitTokenIssuer(LiveKitProperties properties) {
        this.properties = properties;
    }

    public VoiceMediaToken issueForVoiceChannel(
            UUID channelId,
            UUID participantUserId,
            boolean canSpeak,
            boolean canListen
    ) {
        String roomName = voiceRoomName(channelId);
        String participantToken = buildToken(participantUserId, roomName, canSpeak, canListen);
        return new VoiceMediaToken(roomName, properties.url(), participantToken, canSpeak, canListen);
    }

    static String voiceRoomName(UUID channelId) {
        return "voice-" + channelId;
    }

    private String buildToken(UUID participantUserId, String roomName, boolean canSpeak, boolean canListen) {
        AccessToken token = new AccessToken(properties.apiKey(), properties.apiSecret());
        token.setIdentity(participantUserId.toString());
        token.setName(participantUserId.toString());
        token.setTtl(Duration.ofMinutes(15).toMillis());
        token.addGrants(
                new RoomJoin(true),
                new RoomName(roomName),
                new CanPublish(canSpeak),
                new CanSubscribe(canListen)
        );
        return token.toJwt();
    }
}

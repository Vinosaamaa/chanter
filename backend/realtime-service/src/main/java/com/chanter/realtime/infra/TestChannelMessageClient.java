package com.chanter.realtime.infra;

import com.chanter.realtime.application.ChannelMessageClient;
import com.chanter.realtime.application.PersistedChannelMessage;
import com.chanter.realtime.domain.RealtimeChannelScope;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestChannelMessageClient implements ChannelMessageClient {

    private final Clock clock;

    public TestChannelMessageClient(Clock clock) {
        this.clock = clock;
    }

    @Override
    public PersistedChannelMessage postMessage(
            UUID channelId,
            UUID senderUserId,
            RealtimeChannelScope channelScope,
            String body
    ) {
        return new PersistedChannelMessage(
                UUID.randomUUID(),
                channelId,
                senderUserId,
                body,
                clock.instant()
        );
    }
}

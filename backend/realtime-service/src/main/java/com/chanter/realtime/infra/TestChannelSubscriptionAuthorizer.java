package com.chanter.realtime.infra;

import com.chanter.realtime.application.ChannelSubscriptionAuthorizer;
import com.chanter.realtime.domain.RealtimeChannelScope;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("test")
public class TestChannelSubscriptionAuthorizer implements ChannelSubscriptionAuthorizer {

    private final Map<UUID, Set<AccessKey>> grants = new HashMap<>();

    @Override
    public void requireSubscribeAccess(UUID channelId, UUID userId, RealtimeChannelScope channelScope) {
        Set<AccessKey> channelGrants = grants.getOrDefault(channelId, Set.of());
        if (!channelGrants.contains(new AccessKey(userId, channelScope))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Channel subscription denied");
        }
    }

    public void grant(UUID channelId, UUID userId, RealtimeChannelScope channelScope) {
        grants.computeIfAbsent(channelId, ignored -> new HashSet<>())
                .add(new AccessKey(userId, channelScope));
    }

    public void clear() {
        grants.clear();
    }

    private record AccessKey(UUID userId, RealtimeChannelScope channelScope) {
    }
}

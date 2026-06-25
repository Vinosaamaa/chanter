package com.chanter.message.infra;

import com.chanter.message.application.ChannelMessageAccess;
import com.chanter.message.application.ChannelMessageAccessClient;
import com.chanter.message.domain.ChannelScope;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Profile("test")
public class TestChannelMessageAccessClient implements ChannelMessageAccessClient {

    private final Map<AccessKey, ChannelMessageAccess> grants = new HashMap<>();

    @Override
    public ChannelMessageAccess requireAccess(UUID channelId, UUID userId, ChannelScope channelScope) {
        ChannelMessageAccess access = grants.get(new AccessKey(channelId, userId, channelScope));
        if (access == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Channel access denied");
        }
        return access;
    }

    public void grant(UUID channelId, UUID userId, ChannelScope channelScope) {
        grants.put(
                new AccessKey(channelId, userId, channelScope),
                new ChannelMessageAccess(channelId, channelScope, true, true)
        );
    }

    public void clear() {
        grants.clear();
    }

    private record AccessKey(UUID channelId, UUID userId, ChannelScope channelScope) {
    }
}

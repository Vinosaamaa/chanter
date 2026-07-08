package com.chanter.realtime.infra;

import com.chanter.realtime.application.PresenceStore;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class InMemoryPresenceStore implements PresenceStore {

    private final Set<UUID> onlineUsers = ConcurrentHashMap.newKeySet();

    @Override
    public void markOnline(UUID userId) {
        onlineUsers.add(userId);
    }

    @Override
    public void markOffline(UUID userId) {
        onlineUsers.remove(userId);
    }

    @Override
    public boolean isOnline(UUID userId) {
        return onlineUsers.contains(userId);
    }
}

package com.chanter.realtime.infra;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class SocialGraph {

    private final Set<String> friendships = ConcurrentHashMap.newKeySet();

    public void befriend(UUID firstUserId, UUID secondUserId) {
        if (firstUserId.equals(secondUserId)) {
            throw new IllegalArgumentException("Users cannot befriend themselves");
        }
        friendships.add(pairKey(firstUserId, secondUserId));
    }

    public void clear() {
        friendships.clear();
    }

    public boolean areFriends(UUID firstUserId, UUID secondUserId) {
        return friendships.contains(pairKey(firstUserId, secondUserId));
    }

    public List<UUID> friendUserIds(UUID viewerUserId) {
        return friendships.stream()
                .map(pair -> pair.split(":", 2))
                .filter(parts -> parts[0].equals(viewerUserId.toString()) || parts[1].equals(viewerUserId.toString()))
                .map(parts -> parts[0].equals(viewerUserId.toString())
                        ? UUID.fromString(parts[1])
                        : UUID.fromString(parts[0]))
                .toList();
    }

    private static String pairKey(UUID firstUserId, UUID secondUserId) {
        if (firstUserId.compareTo(secondUserId) < 0) {
            return firstUserId + ":" + secondUserId;
        }
        return secondUserId + ":" + firstUserId;
    }
}

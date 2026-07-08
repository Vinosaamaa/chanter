package com.chanter.message.infra;

import com.chanter.message.application.CoMembershipClient;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestCoMembershipClient implements CoMembershipClient {

    private final Set<String> deniedPairs = ConcurrentHashMap.newKeySet();

    public void deny(UUID firstUserId, UUID secondUserId) {
        deniedPairs.add(pairKey(firstUserId, secondUserId));
    }

    public void clear() {
        deniedPairs.clear();
    }

    @Override
    public boolean shareStudyServerMembership(UUID firstUserId, UUID secondUserId) {
        return !deniedPairs.contains(pairKey(firstUserId, secondUserId))
                && !deniedPairs.contains(pairKey(secondUserId, firstUserId));
    }

    private static String pairKey(UUID firstUserId, UUID secondUserId) {
        return firstUserId + ":" + secondUserId;
    }
}

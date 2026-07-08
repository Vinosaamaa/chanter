package com.chanter.realtime.infra;

import com.chanter.realtime.application.SocialFriendsClient;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Profile("test")
public class TestSocialFriendsClient implements SocialFriendsClient {

    private final SocialGraph socialGraph;

    public TestSocialFriendsClient(SocialGraph socialGraph) {
        this.socialGraph = socialGraph;
    }

    @Override
    public Mono<List<UUID>> listFriendUserIds(UUID viewerUserId) {
        return Mono.fromSupplier(() -> socialGraph.friendUserIds(viewerUserId));
    }
}

package com.chanter.realtime.application;

import java.util.List;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface SocialFriendsClient {

    Mono<List<UUID>> listFriendUserIds(UUID viewerUserId);
}

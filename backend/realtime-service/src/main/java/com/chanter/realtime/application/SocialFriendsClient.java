package com.chanter.realtime.application;

import java.util.List;
import java.util.UUID;

public interface SocialFriendsClient {

    List<UUID> listFriendUserIds(UUID viewerUserId);
}

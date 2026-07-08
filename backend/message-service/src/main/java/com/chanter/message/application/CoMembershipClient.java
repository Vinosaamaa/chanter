package com.chanter.message.application;

import java.util.UUID;

public interface CoMembershipClient {

    boolean shareStudyServerMembership(UUID firstUserId, UUID secondUserId);
}

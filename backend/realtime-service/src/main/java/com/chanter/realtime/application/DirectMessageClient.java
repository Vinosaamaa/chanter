package com.chanter.realtime.application;

import java.util.List;
import java.util.UUID;

public interface DirectMessageClient {

    PersistedDirectMessage sendDirectMessage(UUID senderUserId, UUID recipientUserId, String body);
}

package com.chanter.realtime.application;

import java.util.UUID;

public interface DmCallMediaTokenClient {

    DmCallMediaToken issueForCall(UUID callId, UUID participantUserId);
}

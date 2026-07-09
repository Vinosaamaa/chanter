package com.chanter.realtime.infra;

import com.chanter.realtime.application.DmCallMediaToken;
import com.chanter.realtime.application.DmCallMediaTokenClient;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestDmCallMediaTokenClient implements DmCallMediaTokenClient {

    @Override
    public DmCallMediaToken issueForCall(UUID callId, UUID participantUserId) {
        return new DmCallMediaToken(
                "dm-call-" + callId,
                "ws://localhost:7880",
                "test-token-" + participantUserId,
                true,
                true
        );
    }
}

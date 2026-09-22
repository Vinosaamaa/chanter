package com.chanter.agent.infra;

import com.chanter.common.recovery.OrdinaryOperation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keep expiry scheduling separate from the repository required by explicit recovery invalidation. */
@Component @OrdinaryOperation
public class NativeRequestExpiryWorker {
    private final NativeRequestRepository requests;
    public NativeRequestExpiryWorker(NativeRequestRepository requests) { this.requests = requests; }
    @Scheduled(fixedDelay = 60000)
    public void expire() { requests.expire(); }
}

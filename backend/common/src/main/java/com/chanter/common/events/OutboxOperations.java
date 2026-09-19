package com.chanter.common.events;

import com.chanter.common.auth.AuthHeaders;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal/events/outbox")
public class OutboxOperations {
    private final DurableOutbox outbox;
    private final InternalEventAccess access;
    public OutboxOperations(DurableOutbox outbox, @Value("${chanter.internal-service-token}") String token) {
        this.outbox = outbox;
        this.access = new InternalEventAccess(token);
    }
    @GetMapping public DurableOutbox.Stats stats(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token);
        return outbox.stats();
    }
    @GetMapping("/failed") public List<DurableOutbox.Failure> failed(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token);
        return outbox.failures();
    }
    @PostMapping("/{id}/replay") public void replay(@PathVariable UUID id,
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token);
        if (!outbox.replay(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Failed event not found");
    }
}

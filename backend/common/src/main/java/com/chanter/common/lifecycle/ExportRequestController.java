package com.chanter.common.lifecycle;

import com.chanter.common.auth.AuthHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public final class ExportRequestController {
    private final ExportParticipant participant;
    private final InternalLifecycleAccess access;
    private final AccountExportProtocol protocol;
    private final ExportSourceExecution execution;
    public ExportRequestController(ExportParticipant participant, AccountExportProtocol protocol, ExportSourceExecution execution, @Value("${chanter.internal-service-token}") String token) {
        this.participant = participant; this.protocol = protocol; this.execution = execution; this.access = new InternalLifecycleAccess(token);
    }

    @PostMapping("/api/v1/internal/lifecycle/events")
    public ResponseEntity<Void> accept(@RequestBody String body,
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token);
        try {
            var event = protocol.event(body);
            protocol.request(event);
            execution.deliver(event, () -> participant.accept(event));
        }
        catch (IllegalArgumentException failure) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EXPORT_MESSAGE_REJECTED"); }
        catch (ExportSnapshotStore.ExportFailure failure) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "EXPORT_SOURCE_UNAVAILABLE"); }
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build();
    }
}

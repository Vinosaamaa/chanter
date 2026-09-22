package com.chanter.auth.lifecycle;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.lifecycle.AccountExportProtocol;
import com.chanter.common.lifecycle.InternalLifecycleAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal/lifecycle/events")
public class AccountExportReceiptController {
    private final AccountExportJobs jobs;
    private final AccountExportProtocol protocol;
    private final InternalLifecycleAccess access;
    private final AccountDeletionJobs deletions;
    public AccountExportReceiptController(AccountExportJobs jobs, AccountExportProtocol protocol,AccountDeletionJobs deletions,
            @Value("${chanter.internal-service-token}") String token) {
        this.jobs = jobs; this.protocol = protocol; this.deletions=deletions; this.access = new InternalLifecycleAccess(token);
    }
    @PostMapping
    public ResponseEntity<Void> accept(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String token, @RequestBody String raw) {
        access.require(token);
        try {
            var event=protocol.event(raw);
            if(com.chanter.common.lifecycle.AccountDeletionProtocol.RECEIPT.equals(event.kind())) deletions.accept(event);
            else jobs.accept(event);
        }
        catch (IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EXPORT_RECEIPT_REJECTED"); }
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}

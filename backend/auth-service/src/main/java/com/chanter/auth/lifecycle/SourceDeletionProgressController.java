package com.chanter.auth.lifecycle;

import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
public final class SourceDeletionProgressController {
    private final SourceDeletionJobs jobs;
    private final LifecycleSessionAccess access;
    private final TransactionTemplate tx;
    public SourceDeletionProgressController(SourceDeletionJobs jobs,LifecycleSessionAccess access,PlatformTransactionManager transactions) {
        this.jobs=jobs; this.access=access; tx=new TransactionTemplate(transactions);
    }
    @GetMapping("/api/v1/auth/account/source-deletions/{id}")
    public ResponseEntity<SourceDeletionJobs.PublicProgress> get(@RequestHeader(value="Authorization",required=false) String authorization,@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tx.execute(s -> jobs.progress(id,access.require(authorization,false))));
    }
}

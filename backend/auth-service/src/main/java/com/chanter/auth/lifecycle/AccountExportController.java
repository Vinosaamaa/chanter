package com.chanter.auth.lifecycle;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/account/exports")
public class AccountExportController {
    private final AccountExportJobs jobs;
    public AccountExportController(AccountExportJobs jobs) { this.jobs = jobs; }
    @PostMapping
    public ResponseEntity<AccountExportJobs.Job> create(@RequestHeader(value="Authorization",required=false) String authorization,
            @Valid @RequestBody Create request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore()).body(jobs.create(authorization, request.requestId()));
    }
    @GetMapping
    public ResponseEntity<List<AccountExportJobs.Job>> list(@RequestHeader(value="Authorization",required=false) String authorization) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jobs.list(authorization));
    }
    @GetMapping("/{id}")
    public ResponseEntity<AccountExportJobs.Job> get(@RequestHeader(value="Authorization",required=false) String authorization, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jobs.get(authorization, id));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<AccountExportJobs.Job> cancel(@RequestHeader(value="Authorization",required=false) String authorization, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jobs.cancel(authorization, id));
    }
    public record Create(@NotNull UUID requestId) { }
}

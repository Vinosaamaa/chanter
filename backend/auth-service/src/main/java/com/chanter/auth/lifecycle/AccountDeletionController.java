package com.chanter.auth.lifecycle;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import com.chanter.auth.api.BrowserSessionCookies;

@RestController
@RequestMapping("/api/v1/auth/account/deletions")
public class AccountDeletionController {
    private final AccountDeletionJobs jobs;
    private final BrowserSessionCookies cookies;
    public AccountDeletionController(AccountDeletionJobs jobs,BrowserSessionCookies cookies) { this.jobs=jobs; this.cookies=cookies; }
    @PostMapping
    public ResponseEntity<AccountDeletionJobs.Job> prepare(@RequestHeader(value="Authorization",required=false) String authorization,
            @Valid @RequestBody Prepare request) {
        var result=jobs.prepare(authorization,request.requestId());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE,ResponseCookie.from(AccountDeletionJobs.COOKIE,result.handle()).secure(true).httpOnly(true)
                        .sameSite("Strict").path(AccountDeletionJobs.path(result.job().id())).maxAge(AccountDeletionJobs.RECEIPT_RETENTION).build().toString())
                .body(result.job());
    }
    @GetMapping("/{id}")
    public ResponseEntity<AccountDeletionJobs.Job> get(@RequestHeader(value="Authorization",required=false) String authorization,@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jobs.get(authorization,id));
    }
    @PostMapping("/{id}/confirm")
    public ResponseEntity<AccountDeletionJobs.Job> confirm(@RequestHeader(value="Authorization",required=false) String authorization,
            @PathVariable UUID id,@Valid @RequestBody Confirm request) {
        if(!"DELETE MY ACCOUNT".equals(request.confirmation())) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"DELETION_CONFIRMATION_REQUIRED");
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(jobs.confirm(authorization,id));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<AccountDeletionJobs.Job> cancel(@RequestHeader(value="Authorization",required=false) String authorization,@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jobs.cancel(authorization,id));
    }
    @GetMapping("/{id}/receipt")
    public ResponseEntity<AccountDeletionJobs.Job> receipt(@PathVariable UUID id,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jobs.receipt(id,cookies.read(request,AccountDeletionJobs.COOKIE)));
    }
    public record Prepare(@NotNull UUID requestId) { }
    public record Confirm(@NotNull String confirmation) { }
}

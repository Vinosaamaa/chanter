package com.chanter.auth.lifecycle;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.lifecycle.InternalLifecycleAccess;
import com.chanter.common.lifecycle.RecoveryInvalidationStore;
import com.chanter.common.lifecycle.TerminalJournal;
import com.chanter.common.lifecycle.TerminalReapplyStore;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal/lifecycle")
public final class AuthTerminalRecoveryController {
    private final AuthTerminalRecovery recovery;
    private final InternalLifecycleAccess access;
    private final ObjectMapper mapper;
    public AuthTerminalRecoveryController(AuthTerminalRecovery recovery, ObjectMapper mapper,
            @Value("${chanter.internal-service-token}") String token) {
        this.recovery = recovery; this.access = new InternalLifecycleAccess(token);
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    @PostMapping("/journal/reapply")
    public ResponseEntity<TerminalReapplyStore.Receipt> reapply(
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token, @RequestBody String raw) {
        access.require(token);
        try { return response(recovery.reapply(decode(raw, TerminalJournal.Page.class, 256 * 1024))); }
        catch (IllegalArgumentException invalid) { throw rejected(); }
    }
    @GetMapping("/journal/reapply/receipt")
    public ResponseEntity<TerminalReapplyStore.Receipt> receipt(
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token); return response(recovery.receipt());
    }
    @PostMapping("/recovery/invalidate-sessions")
    public ResponseEntity<RecoveryInvalidationStore.Receipt> invalidate(
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token, @RequestBody String raw) {
        access.require(token);
        try { return response(recovery.invalidate(decode(raw, RecoveryInvalidationStore.Request.class, 2048))); }
        catch (IllegalArgumentException invalid) { throw rejected(); }
    }
    private <T> T decode(String raw, Class<T> type, int limit) {
        if (raw.length() > limit) throw rejected();
        try {
            T decoded = mapper.readValue(raw, type);
            if (decoded == null) throw rejected();
            return decoded;
        } catch (java.io.IOException invalid) { throw rejected(); }
    }
    private static <T> ResponseEntity<T> response(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(value);
    }
    private static ResponseStatusException rejected() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "RECOVERY_REQUEST_REJECTED"); }
}

package com.chanter.common.lifecycle;

import com.chanter.common.auth.AuthHeaders;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Imported only by a source that supplies its owning mutation and permanent write fences. */
@RestController
@RequestMapping("/api/v1/internal/lifecycle/journal/reapply")
public final class SourceTerminalRecoveryController {
    private final TerminalReapplyStore participant;
    private final InternalLifecycleAccess access;
    private final ObjectMapper mapper;

    public SourceTerminalRecoveryController(TerminalReapplyStore participant, ObjectMapper mapper,
            @Value("${chanter.internal-service-token}") String token) {
        this.participant = participant; this.access = new InternalLifecycleAccess(token);
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    @PostMapping
    public ResponseEntity<TerminalReapplyStore.Receipt> reapply(
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token, @RequestBody String raw) {
        access.require(token);
        if (raw.length() > 256 * 1024 || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 256 * 1024) throw rejected();
        try {
            var page = mapper.readValue(raw, TerminalJournal.Page.class);
            if (page == null) throw rejected();
            return response(participant.reapply(page));
        } catch (java.io.IOException | IllegalArgumentException invalid) { throw rejected(); }
    }
    @GetMapping("/receipt")
    public ResponseEntity<TerminalReapplyStore.Receipt> receipt(
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token); return response(participant.receipt());
    }
    private static <T> ResponseEntity<T> response(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(value);
    }
    private static ResponseStatusException rejected() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "RECOVERY_REQUEST_REJECTED"); }
}

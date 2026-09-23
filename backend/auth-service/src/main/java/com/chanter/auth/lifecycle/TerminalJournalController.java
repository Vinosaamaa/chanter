package com.chanter.auth.lifecycle;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.lifecycle.InternalLifecycleAccess;
import com.chanter.common.lifecycle.TerminalJournal;
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
@RequestMapping("/api/v1/internal/lifecycle/journal")
public class TerminalJournalController {
    private final TerminalJournalStore journal;
    private final InternalLifecycleAccess access;
    private final ObjectMapper mapper;
    public TerminalJournalController(TerminalJournalStore journal, ObjectMapper mapper,
            @Value("${chanter.internal-service-token}") String token) {
        this.journal = journal; this.access = new InternalLifecycleAccess(token);
        this.mapper = mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @GetMapping
    public ResponseEntity<TerminalJournal.Page> page(
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token,
            @RequestParam(defaultValue="0") long after, @RequestParam(required=false) Long through,
            @RequestParam(defaultValue="100") int limit) {
        access.require(token);
        try { return response(journal.page(after, through, limit)); }
        catch (IllegalArgumentException invalid) { throw rejected(); }
    }

    @GetMapping("/checkpoint")
    public ResponseEntity<CheckpointStatus> checkpoint(
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token) {
        access.require(token);
        return response(new CheckpointStatus(journal.checkpoint()));
    }

    @PostMapping("/checkpoint")
    public ResponseEntity<TerminalJournal.Checkpoint> acknowledge(
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN, required=false) String token, @RequestBody String raw) {
        access.require(token);
        try {
            if (raw.length() > 2048) throw rejected();
            var checkpoint = mapper.readValue(raw, TerminalJournal.Checkpoint.class);
            if (checkpoint == null) throw rejected();
            return response(journal.acknowledge(checkpoint));
        } catch (java.io.IOException | IllegalArgumentException invalid) { throw rejected(); }
    }
    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(body);
    }
    private static ResponseStatusException rejected() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "JOURNAL_REQUEST_REJECTED"); }
    public record CheckpointStatus(TerminalJournal.Checkpoint checkpoint) { }
}

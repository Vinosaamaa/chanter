package com.chanter.common.lifecycle;

import com.chanter.common.auth.AuthHeaders;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Imported only where the owning source supplies terminal-bound scope reconciliation. */
@RestController
@RequestMapping("/api/v1/internal/lifecycle/deleted-study-servers")
public final class SourceDeletedScopeController {
    private final DeletedScopeStore store;
    private final InternalLifecycleAccess access;
    private final ObjectMapper mapper;
    private final RecoveryScopeStore recovery;
    public SourceDeletedScopeController(DeletedScopeStore store,RecoveryScopeStore recovery,ObjectMapper mapper,@Value("${chanter.internal-service-token}") String token) {
        this.store=store; access=new InternalLifecycleAccess(token);
        this.recovery=recovery;
        this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    @PostMapping("/{id}/scope/recovery/import")
    public ResponseEntity<RecoveryScope.Receipt> importRecovery(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String token,
            @PathVariable UUID id,@RequestBody String raw) {
        access.require(token);
        if(raw.length()>32*1024 || raw.getBytes(StandardCharsets.UTF_8).length>32*1024) throw rejected();
        try {
            var request=mapper.readValue(raw,RecoveryScope.Import.class);
            if(request==null) throw rejected();
            request.validate(); if(!id.equals(request.entry().targetId())) throw rejected();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(recovery.accept(request));
        } catch(java.io.IOException | IllegalArgumentException invalid) { throw rejected(); }
    }
    @PostMapping("/{id}/scope/import")
    public ResponseEntity<DeletedScope.Receipt> importPage(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String token,
            @PathVariable UUID id,@RequestBody String raw) {
        access.require(token);
        if(raw.length()>32*1024 || raw.getBytes(StandardCharsets.UTF_8).length>32*1024) throw rejected();
        try {
            var request=mapper.readValue(raw,DeletedScope.Import.class);
            if(request==null) throw rejected();
            request.validate();
            if(!id.equals(request.entry().targetId())) throw rejected();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(store.accept(request));
        } catch(java.io.IOException | IllegalArgumentException invalid) { throw rejected(); }
    }
    private static ResponseStatusException rejected() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"DELETION_SCOPE_REJECTED"); }
}

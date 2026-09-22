package com.chanter.agent.lifecycle;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.lifecycle.InternalLifecycleAccess;
import com.chanter.common.lifecycle.RecoveryInvalidationStore;
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
@RequestMapping("/api/v1/internal/lifecycle/recovery/invalidate-sessions")
public class AgentRecoveryInvalidationController {
    private final AgentRecoveryInvalidation recovery;
    private final InternalLifecycleAccess access;
    private final ObjectMapper mapper;
    public AgentRecoveryInvalidationController(AgentRecoveryInvalidation recovery,ObjectMapper mapper,
            @Value("${chanter.internal-service-token}") String token) {
        this.recovery=recovery; access=new InternalLifecycleAccess(token);
        this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    @PostMapping public ResponseEntity<RecoveryInvalidationStore.Receipt> invalidate(
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String token,@RequestBody String raw) {
        access.require(token);
        if(raw.length()>2048 || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>2048) throw rejected();
        try {
            var request=mapper.readValue(raw,RecoveryInvalidationStore.Request.class);
            if(request==null) throw rejected();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                    .body(recovery.invalidate(request));
        } catch(java.io.IOException | IllegalArgumentException invalid) { throw rejected(); }
    }
    private static ResponseStatusException rejected() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"RECOVERY_REQUEST_REJECTED"); }
}

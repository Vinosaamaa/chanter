package com.chanter.community.lifecycle;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.lifecycle.InternalLifecycleAccess;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/internal/lifecycle/deleted-study-servers")
public class DeletedStudyServerScopeController {
    private final DeletedStudyServerScope scopes;
    private final InternalLifecycleAccess access;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    public DeletedStudyServerScopeController(DeletedStudyServerScope scopes,com.fasterxml.jackson.databind.ObjectMapper mapper,
            @Value("${chanter.internal-service-token}") String token) {
        this.scopes=scopes; access=new InternalLifecycleAccess(token);
        this.mapper=mapper.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    @PostMapping("/{id}/scope/import")
    public ResponseEntity<com.chanter.common.lifecycle.DeletedScope.Receipt> importPage(
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String token,@PathVariable UUID id,@RequestBody String raw) {
        access.require(token);
        if(raw.length()>32*1024 || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32*1024) throw rejected();
        try {
            var request=mapper.readValue(raw,com.chanter.common.lifecycle.DeletedScope.Import.class);
            if(request==null) throw rejected();
            request.validate();
            if(!id.equals(request.entry().targetId())) throw rejected();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(scopes.importPage(request));
        } catch(java.io.IOException | IllegalArgumentException invalid) { throw rejected(); }
    }
    @GetMapping("/{id}/scope")
    public ResponseEntity<com.chanter.common.lifecycle.DeletedScope.Page> page(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String token,
            @PathVariable UUID id,@RequestParam long revision,@RequestParam UUID eventId,@RequestParam String digest,
            @RequestParam String kind,@RequestParam(defaultValue="00000000-0000-0000-0000-000000000000") UUID after,
            @RequestParam(defaultValue="256") int limit) {
        access.require(token);
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                    .body(scopes.page(id,revision,eventId,digest,kind,after,limit));
        } catch(IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"DELETION_SCOPE_REJECTED"); }
    }
    private static ResponseStatusException rejected() { return new ResponseStatusException(HttpStatus.BAD_REQUEST,"DELETION_SCOPE_REJECTED"); }
}

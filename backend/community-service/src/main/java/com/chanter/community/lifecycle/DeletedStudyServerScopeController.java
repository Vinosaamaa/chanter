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
    public DeletedStudyServerScopeController(DeletedStudyServerScope scopes,@Value("${chanter.internal-service-token}") String token) {
        this.scopes=scopes; access=new InternalLifecycleAccess(token);
    }
    @GetMapping("/{id}/scope")
    public ResponseEntity<DeletedStudyServerScope.Page> page(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String token,
            @PathVariable UUID id,@RequestParam long revision,@RequestParam UUID eventId,@RequestParam String digest,
            @RequestParam String kind,@RequestParam(defaultValue="00000000-0000-0000-0000-000000000000") UUID after,
            @RequestParam(defaultValue="256") int limit) {
        access.require(token);
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                    .body(scopes.page(id,revision,eventId,digest,kind,after,limit));
        } catch(IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"DELETION_SCOPE_REJECTED"); }
    }
}

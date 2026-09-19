package com.chanter.auth.moderation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform-admin/appeals")
public class PlatformAppealsController {
    private final ModerationAppeals appeals;
    public PlatformAppealsController(ModerationAppeals appeals) { this.appeals=appeals; }
    @GetMapping
    List<ModerationAppeals.Appeal> list(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Chanter-Operator-Verification") String verification,@RequestParam String reason,
            @RequestParam(defaultValue="0") int offset,@RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        return appeals.list(authorization,verification,reason,offset,correlation);
    }
    @PostMapping("/{id}/resolution")
    void resolve(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Chanter-Operator-Verification") String verification,@PathVariable UUID id,
            @Valid @RequestBody Resolution request,@RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        appeals.resolve(authorization,verification,id,request.status(),request.reason(),request.confirmation(),correlation);
    }
    record Resolution(@NotBlank String status,@NotBlank @Size(max=2000) String reason,@NotBlank String confirmation) { }
}

package com.chanter.auth.moderation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform-admin/operators")
public class OperatorRolesController {
    private final OperatorRoles roles;
    public OperatorRolesController(OperatorRoles roles) { this.roles = roles; }

    @GetMapping
    List<OperatorRoles.OperatorRow> list(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Chanter-Operator-Verification") String verification, @RequestParam String reason,
            @RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        return roles.list(authorization, verification, reason, correlation);
    }

    @PutMapping("/{userId}")
    void change(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Chanter-Operator-Verification") String verification,
            @PathVariable UUID userId, @Valid @RequestBody Change request,
            @RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        roles.change(authorization, verification, userId, request.role(), request.reason(), request.confirmation(), correlation);
    }

    record Change(String role, @NotBlank @Size(max=2000) String reason, @NotBlank String confirmation) { }
}

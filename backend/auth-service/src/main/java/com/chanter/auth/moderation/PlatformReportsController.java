package com.chanter.auth.moderation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform-admin/reports")
public class PlatformReportsController {
    private final ModerationCases cases;
    public PlatformReportsController(ModerationCases cases){this.cases=cases;}

    @GetMapping
    List<ModerationCases.QueueItem> queue(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Chanter-Operator-Verification") String verification,
            @RequestParam(required=false) String status,@RequestParam String reason,
            @RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        return cases.queue(authorization,verification,status,reason,correlation);
    }
    @GetMapping("/{id}")
    ModerationCases.Detail detail(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Chanter-Operator-Verification") String verification,@PathVariable UUID id,
            @RequestParam String reason,@RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        return cases.detail(authorization,verification,id,reason,correlation);
    }
    @PostMapping("/{id}/assignment")
    void assign(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Chanter-Operator-Verification") String verification,@PathVariable UUID id,
            @Valid @RequestBody Assignment request,@RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        cases.assign(authorization,verification,id,request.assignee(),request.reason(),correlation);
    }
    @PostMapping("/{id}/notes")
    void note(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Chanter-Operator-Verification") String verification,@PathVariable UUID id,
            @Valid @RequestBody Note request,@RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        cases.note(authorization,verification,id,request.body(),request.reason(),correlation);
    }
    @PostMapping("/{id}/resolution")
    void resolve(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Chanter-Operator-Verification") String verification,@PathVariable UUID id,
            @Valid @RequestBody Resolution request,@RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        cases.resolve(authorization,verification,id,request.status(),request.resolution(),request.reason(),correlation);
    }
    record Assignment(@NotNull UUID assignee,@NotBlank @Size(max=2000) String reason){}
    @PostMapping("/{id}/restrictions")
    void restrict(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Chanter-Operator-Verification") String verification,@PathVariable UUID id,
            @Valid @RequestBody Restriction request,@RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        cases.restrict(authorization,verification,id,request.operationId(),request.type(),request.targetId(),
                request.reason(),request.expiresAt(),request.confirmation(),correlation);
    }
    @PostMapping("/{id}/restrictions/{restriction}/reinstatement")
    void reinstate(@RequestHeader("Authorization") String authorization,
            @RequestHeader("X-Chanter-Operator-Verification") String verification,@PathVariable UUID id,
            @PathVariable UUID restriction,@Valid @RequestBody Reinstatement request,
            @RequestAttribute(ModerationRequestContext.CORRELATION) UUID correlation) {
        cases.reinstate(authorization,verification,id,restriction,request.reason(),request.confirmation(),correlation);
    }
    record Restriction(@NotNull UUID operationId,@NotBlank String type,@NotNull UUID targetId,
            @NotBlank @Size(max=2000) String reason,@NotNull Instant expiresAt,@NotBlank String confirmation){}
    record Reinstatement(@NotBlank @Size(max=2000) String reason,@NotBlank String confirmation){}
    record Note(@NotBlank @Size(max=4000) String body,@NotBlank @Size(max=2000) String reason){}
    record Resolution(@NotBlank String status,@NotBlank @Size(max=2000) String resolution,@NotBlank @Size(max=2000) String reason){}
}

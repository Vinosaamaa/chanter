package com.chanter.auth.moderation;

import com.chanter.auth.application.AuthSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/moderation/reports")
public class ModerationReportsController {
    private final AuthSessionService sessions;
    private final ModerationCases cases;
    public ModerationReportsController(AuthSessionService sessions,ModerationCases cases){this.sessions=sessions;this.cases=cases;}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ModerationCases.Report submit(@RequestHeader(value="Authorization",required=false) String authorization,
            @Valid @RequestBody ReportRequest request) {
        return cases.submit(sessions.requireUserIdFromAccessToken(authorization),request.targetType(),request.targetId(),request.reason());
    }
    @GetMapping
    List<ModerationCases.Report> mine(@RequestHeader(value="Authorization",required=false) String authorization) {
        return cases.ownReports(sessions.requireUserIdFromAccessToken(authorization));
    }
    @GetMapping("/{id}")
    ModerationCases.Report mine(@RequestHeader(value="Authorization",required=false) String authorization,@PathVariable UUID id) {
        return cases.ownReport(sessions.requireUserIdFromAccessToken(authorization),id);
    }
    record ReportRequest(@NotBlank String targetType,@NotNull UUID targetId,@NotBlank @Size(max=2000) String reason){}
}

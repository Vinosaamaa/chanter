package com.chanter.community.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.ReportEvidence;
import com.chanter.community.application.StudyServerRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InternalReportEvidenceController {
    private final byte[] token;
    private final StudyServerRepository servers;

    public InternalReportEvidenceController(@Value("${chanter.internal-service-token}") String token,
            StudyServerRepository servers) {
        this.token = InternalServiceTokens.requireBytes(token);
        this.servers = servers;
    }

    @GetMapping("/internal/v1/moderation/evidence/STUDY_SERVER/{id}")
    ReportEvidence evidence(@RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String presented,
            @PathVariable UUID id, @RequestParam UUID viewerId) {
        if (!MessageDigest.isEqual(token, (presented == null ? "" : presented).getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal service authentication required");
        }
        if (!servers.isStudyServerMember(id, viewerId)) throw missing();
        var server = servers.findById(id).orElseThrow(InternalReportEvidenceController::missing);
        String description = server.description() == null ? "" : server.description();
        return new ReportEvidence("STUDY_SERVER", id, server.ownerRole().userId(), id, null, null,
                server.name().substring(0, Math.min(255, server.name().length())),
                description.substring(0, Math.min(8000, description.length())), null);
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Report source unavailable");
    }
}

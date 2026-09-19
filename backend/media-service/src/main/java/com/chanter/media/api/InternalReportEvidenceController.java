package com.chanter.media.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.ReportEvidence;
import com.chanter.media.application.CourseResourceService;
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
    private final CourseResourceService resources;

    public InternalReportEvidenceController(@Value("${chanter.internal-service-token}") String token, CourseResourceService resources) {
        this.token=InternalServiceTokens.requireBytes(token);
        this.resources=resources;
    }

    @GetMapping("/internal/v1/moderation/evidence/RESOURCE/{id}")
    ReportEvidence evidence(@RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String presented,
            @PathVariable UUID id,@RequestParam UUID viewerId) {
        if(!MessageDigest.isEqual(token,(presented==null?"":presented).getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Internal service authentication required");
        return resources.reportEvidence(id,viewerId);
    }
}

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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public InternalReportEvidenceController(@Value("${chanter.internal-service-token}") String token,
            StudyServerRepository servers,org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.token = InternalServiceTokens.requireBytes(token);
        this.servers = servers;
        this.jdbc = jdbc;
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

    @GetMapping("/internal/v1/moderation/directory")
    java.util.List<com.chanter.common.auth.ModerationDirectoryItem> directory(
            @RequestHeader(value=AuthHeaders.INTERNAL_SERVICE_TOKEN,required=false) String presented,
            @RequestParam String query,@RequestParam(defaultValue="0") int offset) {
        if(!MessageDigest.isEqual(token,(presented==null?"":presented).getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Internal service authentication required");
        if(query.strip().length()<3 || query.length()>120 || offset<0 || offset>10000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid directory query");
        String pattern="%"+query.strip().toLowerCase(java.util.Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return jdbc.query("SELECT id,name FROM study_servers WHERE LOWER(name) LIKE ? ESCAPE '!' OR CAST(id AS VARCHAR)=? ORDER BY name,id LIMIT 50 OFFSET ?",
                (rs,row)->new com.chanter.common.auth.ModerationDirectoryItem("STUDY_SERVER",rs.getObject(1,UUID.class),rs.getString(2)),pattern,query.strip().toLowerCase(java.util.Locale.ROOT),offset);
    }
}

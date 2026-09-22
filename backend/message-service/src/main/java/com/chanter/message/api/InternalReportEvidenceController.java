package com.chanter.message.api;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.ReportEvidence;
import com.chanter.message.application.ChannelMessageAccess;
import com.chanter.message.application.ChannelMessageAccessClient;
import com.chanter.message.domain.ChannelScope;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InternalReportEvidenceController {
    private final byte[] token;
    private final JdbcTemplate jdbc;
    private final ChannelMessageAccessClient channels;

    public InternalReportEvidenceController(@Value("${chanter.internal-service-token}") String token,
            JdbcTemplate jdbc, ChannelMessageAccessClient channels) {
        this.token = InternalServiceTokens.requireBytes(token);
        this.jdbc = jdbc;
        this.channels = channels;
    }

    @GetMapping("/internal/v1/moderation/evidence/{type}/{id}")
    ReportEvidence evidence(@RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String presented,
            @PathVariable String type, @PathVariable UUID id, @RequestParam UUID viewerId) {
        if (!MessageDigest.isEqual(token, (presented == null ? "" : presented).getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal service authentication required");
        }
        return switch (type) {
            case "DM" -> directMessage(id, viewerId);
            case "MESSAGE" -> channelMessage(id, viewerId);
            default -> throw missing();
        };
    }

    private ReportEvidence directMessage(UUID id, UUID viewer) {
        // Blocking or ending a friendship must not remove a participant's ability to report saved evidence.
        return jdbc.query("""
                SELECT sender_user_id, body FROM direct_messages
                WHERE id = ? AND (sender_user_id = ? OR recipient_user_id = ?)
                """, (row, index) -> snapshot("DM", id, row.getObject("sender_user_id", UUID.class),
                        null, null, null, row.getString("body")), id, viewer, viewer)
                .stream().findFirst().orElseThrow(InternalReportEvidenceController::missing);
    }

    private ReportEvidence channelMessage(UUID id, UUID viewer) {
        Message message = jdbc.query("SELECT channel_id, sender_user_id, body FROM channel_messages WHERE id = ?",
                (row, index) -> new Message(row.getObject("channel_id", UUID.class),
                        row.getObject("sender_user_id", UUID.class), row.getString("body")), id)
                .stream().findFirst().orElseThrow(InternalReportEvidenceController::missing);
        ChannelMessageAccess access;
        try {
            access = channels.requireAccess(message.channel(), viewer, ChannelScope.COURSE);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != 404) throw exception;
            access = channels.requireAccess(message.channel(), viewer, ChannelScope.STUDY_SERVER);
        }
        if (!access.channelId().equals(message.channel()) || !access.canReadMessages()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current channel access required");
        }
        return snapshot("MESSAGE", id, message.author(), access.studyServerId(), access.courseId(),
                message.channel(), message.body());
    }

    private static ReportEvidence snapshot(String type, UUID id, UUID author, UUID server, UUID course,
            UUID channel, String body) {
        try {
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
            return new ReportEvidence(type, id, author, server, course, channel,
                    type.equals("DM") ? "Direct message" : "Channel message",
                    body.substring(0, Math.min(8000, body.length())), fingerprint);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Report source unavailable");
    }

    private record Message(UUID channel, UUID author, String body) { }
}

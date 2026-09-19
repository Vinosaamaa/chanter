package com.chanter.auth.moderation;

import com.chanter.auth.application.AuthUserRepository;
import com.chanter.auth.application.EmailSender;
import com.chanter.common.auth.ReportEvidence;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ModerationAppeals {
    private final JdbcTemplate jdbc;
    private final AuthUserRepository users;
    private final EmailSender email;
    private final ObjectMapper mapper;
    private final ModerationAudit audit;
    private final String publicBaseUrl;
    private final SecureRandom random = new SecureRandom();

    public ModerationAppeals(JdbcTemplate jdbc, AuthUserRepository users, EmailSender email,
            ObjectMapper mapper, ModerationAudit audit,
            @Value("${chanter.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.jdbc = jdbc;
        this.users = users;
        this.email = email;
        this.mapper = mapper;
        this.audit = audit;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void request(String address, UUID restriction) {
        var user = users.findByEmail(address.strip().toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null || !user.emailVerified() || !ownsRestriction(user.id(), restriction)) return;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires = Instant.now().plusSeconds(20 * 60);
        jdbc.update("INSERT INTO moderation_appeal_tokens(token_hash,user_id,restriction_id,expires_at) VALUES(?,?,?,?)",
                OperatorAccess.hash(token), user.id(), restriction, expires.atOffset(ZoneOffset.UTC));
        email.send(user.email(), "Review your Chanter restriction",
                "Use this one-time link within 20 minutes to submit an appeal. It does not sign you in.\n\n"
                        + publicBaseUrl + "/appeal#token=" + token, expires);
    }

    @Transactional
    public void submit(String token, String body, UUID correlation) {
        if (token == null || token.length() > 128 || body == null || body.isBlank() || body.length() > 4000) {
            throw invalid();
        }
        Instant now = Instant.now();
        var credential = jdbc.query("""
                SELECT user_id,restriction_id,expires_at,consumed_at FROM moderation_appeal_tokens
                WHERE token_hash=? FOR UPDATE
                """, (rs, row) -> new Credential(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, OffsetDateTime.class).toInstant(), rs.getObject(4, OffsetDateTime.class)),
                OperatorAccess.hash(token)).stream().findFirst().orElseThrow(ModerationAppeals::invalid);
        if (credential.consumed() != null || !credential.expires().isAfter(now)
                || !ownsRestriction(credential.user(), credential.restriction())) throw invalid();
        jdbc.update("UPDATE moderation_appeal_tokens SET consumed_at=? WHERE token_hash=?",
                now.atOffset(ZoneOffset.UTC), OperatorAccess.hash(token));
        UUID appeal = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO moderation_appeals(id,restriction_id,user_id,body,status,created_at)
                VALUES(?,?,?,?,'PENDING',?)
                """, appeal, credential.restriction(), credential.user(), body.strip(), now.atOffset(ZoneOffset.UTC));
        audit.append(credential.user(), "APPEAL_SUBMITTED", credential.restriction().toString(),
                "Verified account submitted an appeal", correlation, "", appeal.toString());
    }

    private boolean ownsRestriction(UUID user, UUID restriction) {
        return jdbc.query("""
                SELECT r.target_type,r.target_id,p.evidence FROM moderation_restrictions r
                JOIN moderation_reports p ON p.id=r.report_id WHERE r.id=?
                """, (rs, row) -> {
                    if (rs.getString(1).equals("USER")) return user.equals(rs.getObject(2, UUID.class));
                    try {
                        return user.equals(mapper.readValue(rs.getString(3), ReportEvidence.class).authorId());
                    } catch (JsonProcessingException invalid) {
                        throw new IllegalStateException("Unable to verify preserved restriction ownership", invalid);
                    }
                }, restriction).stream().findFirst().orElse(false);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This appeal link is unavailable. Request a new link and try again.");
    }

    private record Credential(UUID user, UUID restriction, Instant expires, OffsetDateTime consumed) { }
}

package com.chanter.auth.moderation;

import com.chanter.auth.application.AuthUserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OperatorRoles {
    private final JdbcTemplate jdbc;
    private final OperatorAccess access;
    private final AuthUserRepository users;
    private final ModerationRestrictions restrictions;
    private final ModerationAudit audit;

    public OperatorRoles(JdbcTemplate jdbc, OperatorAccess access, AuthUserRepository users,
            ModerationRestrictions restrictions, ModerationAudit audit) {
        this.jdbc = jdbc; this.access = access; this.users = users; this.restrictions = restrictions; this.audit = audit;
    }

    /** Called only by the explicit non-web bootstrap command. There is no HTTP equivalent. */
    @Transactional
    public void bootstrap(UUID target, String reason, UUID correlation) {
        lockGrants();
        requireReason(reason);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM platform_operators", Long.class) != 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An operator has already been bootstrapped");
        requireEligible(target);
        jdbc.update("INSERT INTO platform_operators(user_id,role,granted_at) VALUES(?,'ADMIN',?)", target, OffsetDateTime.now(ZoneOffset.UTC));
        audit.append(null, "OPERATOR_BOOTSTRAPPED", target.toString(), reason, correlation, "NONE", "ADMIN_PENDING_FACTOR");
    }

    @Transactional
    public void change(String authorization, String stepUp, UUID target, String role, String reason,
            String confirmation, UUID correlation) {
        lockGrants();
        var actor = access.requireStepUp(authorization, stepUp);
        actor.requireAdmin();
        requireReason(reason);
        if (!target.toString().equals(confirmation)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confirm the target account");
        if (target.equals(actor.userId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Another administrator must change your role");
        if (role != null && !List.of("REVIEWER", "ADMIN").contains(role))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported platform role");
        var prior = jdbc.query("SELECT role,revoked_at FROM platform_operators WHERE user_id=?",
                (rs, row) -> rs.getObject(2) == null ? rs.getString(1) : "REVOKED", target);
        String before = prior.isEmpty() ? "NONE" : prior.getFirst();
        if (role == null) {
            if (prior.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Operator not found");
            jdbc.update("""
                    UPDATE platform_operators SET revoked_at=?,factor_ciphertext=NULL,factor_confirmed=FALSE,last_factor_counter=-1
                    WHERE user_id=?
                    """, OffsetDateTime.now(ZoneOffset.UTC), target);
        } else {
            requireEligible(target);
            if (prior.isEmpty()) {
                jdbc.update("INSERT INTO platform_operators(user_id,role,granted_at) VALUES(?,?,?)", target, role, OffsetDateTime.now(ZoneOffset.UTC));
            } else {
                jdbc.update("UPDATE platform_operators SET role=?,granted_at=?,revoked_at=NULL WHERE user_id=?",
                        role, OffsetDateTime.now(ZoneOffset.UTC), target);
            }
        }
        jdbc.update("DELETE FROM platform_step_up WHERE user_id=?", target);
        audit.append(actor.userId(), role == null ? "OPERATOR_REVOKED" : "OPERATOR_GRANTED", target.toString(), reason,
                correlation, before, role == null ? "REVOKED" : role);
    }

    @Transactional
    public List<OperatorRow> list(String authorization, String stepUp, String reason, UUID correlation) {
        var actor = access.requireStepUp(authorization, stepUp);
        actor.requireAdmin();
        requireReason(reason);
        audit.append(actor.userId(), "OPERATORS_READ", "platform-operators", reason, correlation, "", "");
        return jdbc.query("SELECT user_id,role,revoked_at,factor_confirmed FROM platform_operators ORDER BY granted_at,user_id LIMIT 100",
                (rs, row) -> new OperatorRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3) != null, rs.getBoolean(4)));
    }

    private void requireEligible(UUID user) {
        restrictions.requireActiveAccount(user);
        var account = users.findById(user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (!account.emailVerified() || account.passwordHash() == null || account.passwordHash().isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Operator accounts require verified email and a password");
    }

    private void lockGrants() { jdbc.queryForObject("SELECT id FROM platform_operator_lock WHERE id=1 FOR UPDATE", Integer.class); }
    static void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 2000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason of at most 2000 characters is required");
    }
    public record OperatorRow(UUID userId, String role, boolean revoked, boolean factorEnrolled) { }
}

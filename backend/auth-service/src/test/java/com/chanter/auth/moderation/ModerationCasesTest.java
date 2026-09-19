package com.chanter.auth.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.chanter.common.auth.ReportEvidence;
import java.util.UUID;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:moderation-cases;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ModerationCasesTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ModerationCases cases;
    @Autowired ModerationRestrictions restrictions;
    @MockitoBean ReportEvidenceClient sources;
    @MockitoBean OperatorAccess operators;

    @Test void unauthorizedEvidenceCannotCreateAReportOrPreservedSnapshot() {
        UUID reporter = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        when(sources.read(reporter,"DM",source)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> cases.submit(reporter,"DM",source,"Abusive message"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_reports WHERE target_id=?",Integer.class,source)).isZero();
    }

    @Test void reviewerSeesOnlyAssignedCaseAndEvidenceReadIsAudited() {
        UUID reporter = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        when(sources.read(reporter,"MESSAGE",source)).thenReturn(new ReportEvidence("MESSAGE",source,UUID.randomUUID(),
                UUID.randomUUID(),null,UUID.randomUUID(),"Channel message","Reported text",null));
        var report = cases.submit(reporter,"MESSAGE",source,"Abusive message");
        when(operators.requireStepUp("reviewer","verified")).thenReturn(new OperatorAccess.Operator(reviewer,OperatorAccess.Role.REVIEWER));
        assertThatThrownBy(() -> cases.detail("reviewer","verified",report.id(),"Investigate report",UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
        jdbc.update("INSERT INTO auth_users(id,email,password_hash,display_name,email_verified,created_at) VALUES(?,?,?,'Reviewer',TRUE,CURRENT_TIMESTAMP)",
                reviewer,reviewer+"@cases.test","test-only");
        jdbc.update("INSERT INTO platform_operators(user_id,role,granted_at) VALUES(?,'REVIEWER',CURRENT_TIMESTAMP)",reviewer);
        jdbc.update("UPDATE moderation_reports SET assigned_to=?,status='ASSIGNED' WHERE id=?",reviewer,report.id());
        var evidence = cases.detail("reviewer","verified",report.id(),"Investigate assigned report",UUID.randomUUID());
        assertThat(evidence.evidence().excerpt()).isEqualTo("Reported text");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_audit WHERE action='EVIDENCE_READ' AND actor_id=?",Integer.class,reviewer)).isEqualTo(1);
        assertThatThrownBy(() -> cases.ownReport(UUID.randomUUID(),report.id())).isInstanceOf(ResponseStatusException.class);
        assertThat(cases.ownReport(reporter,report.id()).status()).isEqualTo("ASSIGNED");
    }

    @Test void enforcementCannotEscapeCaseEvidenceOrReviewerAuthority() {
        UUID author=UUID.randomUUID(),source=UUID.randomUUID(),reviewer=operator("REVIEWER");
        when(sources.read(author,"MESSAGE",source)).thenReturn(new ReportEvidence("MESSAGE",source,author,
                UUID.randomUUID(),null,UUID.randomUUID(),"Channel message","Reported text",null));
        var report=cases.submit(author,"MESSAGE",source,"Abusive message");
        jdbc.update("UPDATE moderation_reports SET assigned_to=?,status='ASSIGNED' WHERE id=?",reviewer,report.id());
        when(operators.requireStepUp("reviewer","verified")).thenReturn(new OperatorAccess.Operator(reviewer,OperatorAccess.Role.REVIEWER));
        UUID unrelated=UUID.randomUUID();
        assertThatThrownBy(() -> cases.restrict("reviewer","verified",report.id(),UUID.randomUUID(),"MESSAGE",unrelated,
                "Quarantine",Instant.now().plusSeconds(600),unrelated.toString(),UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> cases.restrict("reviewer","verified",report.id(),UUID.randomUUID(),"USER",author,
                "Suspend author",Instant.now().plusSeconds(600),author.toString(),UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        UUID operation=UUID.randomUUID();
        cases.restrict("reviewer","verified",report.id(),operation,"MESSAGE",source,"Quarantine reported content",
                Instant.now().plusSeconds(600),source.toString(),UUID.randomUUID());
        assertThat(restrictions.isRestricted("MESSAGE",source,Instant.now())).isTrue();
        cases.reinstate("reviewer","verified",report.id(),operation,"Restriction reviewed",source.toString(),UUID.randomUUID());
        assertThat(restrictions.isRestricted("MESSAGE",source,Instant.now())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_audit WHERE target=? AND action LIKE 'RESTRICTION_%'",Integer.class,"MESSAGE:"+source)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT evidence FROM moderation_reports WHERE id=?",String.class,report.id())).contains("Reported text");
    }

    @Test void administratorStillNeedsConfirmationAndTheExactReportedTarget() {
        UUID author=UUID.randomUUID(),source=UUID.randomUUID(),admin=operator("ADMIN");
        when(sources.read(author,"MESSAGE",source)).thenReturn(new ReportEvidence("MESSAGE",source,author,
                UUID.randomUUID(),null,UUID.randomUUID(),"Message","Evidence",null));
        var report=cases.submit(author,"MESSAGE",source,"Report");
        when(operators.requireStepUp("admin","verified")).thenReturn(new OperatorAccess.Operator(admin,OperatorAccess.Role.ADMIN));
        assertThatThrownBy(() -> cases.restrict("admin","verified",report.id(),UUID.randomUUID(),"MESSAGE",source,
                "Quarantine",Instant.now().plusSeconds(600),"wrong",UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        UUID unrelated=UUID.randomUUID();
        assertThatThrownBy(() -> cases.restrict("admin","verified",report.id(),UUID.randomUUID(),"USER",unrelated,
                "Suspend unrelated user",Instant.now().plusSeconds(600),unrelated.toString(),UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_restrictions WHERE report_id=?",Integer.class,report.id())).isZero();
    }

    private UUID operator(String role) {
        UUID user=UUID.randomUUID();
        jdbc.update("INSERT INTO auth_users(id,email,password_hash,display_name,email_verified,created_at) VALUES(?,?,?,'Operator',TRUE,CURRENT_TIMESTAMP)",
                user,user+"@cases.test","test-only");
        jdbc.update("INSERT INTO platform_operators(user_id,role,granted_at) VALUES(?,?,CURRENT_TIMESTAMP)",user,role);
        return user;
    }
}

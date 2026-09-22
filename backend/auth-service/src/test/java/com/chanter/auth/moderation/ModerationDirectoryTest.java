package com.chanter.auth.moderation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:moderation-directory;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class ModerationDirectoryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ModerationDirectory directory;
    @MockitoBean OperatorAccess operators;
    @MockitoBean ReportEvidenceClient sources;

    @Test void directorySearchIsRestrictedBoundedAndAuditedWithoutReturningAccountSecrets() {
        UUID admin=UUID.randomUUID(),user=UUID.randomUUID();
        when(operators.requireStepUp("admin","verified")).thenReturn(new OperatorAccess.Operator(admin,OperatorAccess.Role.ADMIN));
        when(operators.requireStepUp("reviewer","verified")).thenReturn(new OperatorAccess.Operator(UUID.randomUUID(),OperatorAccess.Role.REVIEWER));
        jdbc.update("INSERT INTO auth_users(id,email,password_hash,display_name,email_verified,created_at) VALUES(?,?,?,'Distinct learner',TRUE,CURRENT_TIMESTAMP)",user,user+"@directory.test","private-hash");
        assertThat(directory.search("admin","verified","USER","Distinct",0,"Locate reported account",UUID.randomUUID())).extracting(item -> item.id()).containsExactly(user);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM moderation_audit WHERE actor_id=? AND action='MODERATION_DIRECTORY_READ'",Integer.class,admin)).isEqualTo(1);
        assertThat(directory.search("admin","verified","USER",user.toString().toUpperCase(java.util.Locale.ROOT),0,"Find exact account",UUID.randomUUID()))
                .extracting(item -> item.id()).containsExactly(user);
        assertThatThrownBy(() -> directory.search("reviewer","verified","USER","Distinct",0,"Search",UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> directory.search("admin","verified","USER","x",0,"Search",UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> directory.search("admin","verified","USER","Distinct",10001,"Search",UUID.randomUUID())).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(sources);
    }
}

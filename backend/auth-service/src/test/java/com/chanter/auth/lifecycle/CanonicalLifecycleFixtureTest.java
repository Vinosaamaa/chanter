package com.chanter.auth.lifecycle;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:canonical-fixture;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "chanter.events.dispatch-enabled=false","chanter.email.worker-enabled=false"})
@ActiveProfiles("test")
class CanonicalLifecycleFixtureTest {
    @Autowired ConfigurableApplicationContext context;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Test void unshippedFixtureUsesRealRecentSessionPreparationAndCannotInventCommunityApproval() throws Exception {
        Path source=Path.of("../../scripts/deploy/fixtures/CanonicalLifecycleFixture.java").toAbsolutePath().normalize();
        Path output=Path.of("target/canonical-fixture-test").toAbsolutePath(); Files.createDirectories(output);
        var compiler=ToolProvider.getSystemJavaCompiler();
        try(var files=compiler.getStandardFileManager(null,null,null)) {
            assertThat(compiler.getTask(null,files,null,List.of("-classpath",System.getProperty("java.class.path"),"-d",output.toString()),null,
                    files.getJavaFileObjects(source)).call()).isTrue();
        }
        try(var loader=new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()},getClass().getClassLoader())) {
            Class<?> type=loader.loadClass("CanonicalLifecycleFixture");
            var constructor=type.getDeclaredConstructor(ConfigurableApplicationContext.class); constructor.setAccessible(true);
            Object fixture=constructor.newInstance(context);
            var execute=type.getDeclaredMethod("execute",com.fasterxml.jackson.databind.JsonNode.class); execute.setAccessible(true);
            UUID alias=UUID.randomUUID(),job=UUID.randomUUID();
            var seeded=mapper.valueToTree(execute.invoke(fixture,mapper.valueToTree(Map.of("action","auth-seed","alias",alias.toString()))));
            UUID account=UUID.fromString(seeded.get("accountId").asText());
            assertThat(seeded.toString()).doesNotContain("Token","password","handle");
            var prepared=mapper.valueToTree(execute.invoke(fixture,mapper.valueToTree(Map.of("action","account-prepare","alias",alias.toString(),"jobId",job.toString()))));
            assertThat(prepared.get("id").asText()).isEqualTo(job.toString());
            assertThat(prepared.get("state").asText()).isEqualTo("PREPARING");
            assertThat(prepared.toString()).doesNotContain("Token","password","handle");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions WHERE user_id=? AND revoked_at IS NULL",Integer.class,account)).isPositive();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lifecycle_terminal_journal WHERE target_id=?",Integer.class,account)).isZero();
            assertThatThrownBy(() -> execute.invoke(fixture,mapper.valueToTree(Map.of("action","account-confirm","alias",alias.toString(),"jobId",job.toString()))))
                    .hasRootCauseMessage("409 CONFLICT \"DELETION_NOT_PREPARED\"");
            var events=mapper.valueToTree(execute.invoke(fixture,mapper.valueToTree(Map.of("action","events","afterRevision",0))));
            assertThat(events.size()).isEqualTo(1);
            assertThat(events.get(0).get("destination").asText()).isEqualTo("lifecycle-community");
            assertThat(events.get(0).get("event").get("producer").asText()).isEqualTo("auth");
            assertThatThrownBy(() -> execute.invoke(fixture,mapper.valueToTree(Map.of("action","journal","afterRevision",0,"extra",true))))
                    .hasRootCauseMessage("Unexpected fixture fields");
        }
    }
}

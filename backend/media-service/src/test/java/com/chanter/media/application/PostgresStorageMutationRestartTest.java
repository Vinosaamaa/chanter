package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Native owning-database durability only; this synthetic fixture does not establish canonical journal authority. */
@EnabledIfEnvironmentVariable(named="MEDIA_INTEGRATION",matches="true")
class PostgresStorageMutationRestartTest {
    private static final String SCHEMA="recovery_342_native_restart";
    private static final UUID INVENTORY=UUID.fromString("34200000-0000-4000-8000-000000000001");
    private static final String KEY="resources/v1/34200000-0000-4000-8000-000000000002/34200000-0000-4000-8000-000000000003/34200000-0000-4000-8000-000000000004";
    @Test void unknownDeleteAndItsFenceSurviveAnActualPostgresProcessRestart() {
        var admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:5544/chanter_media","media_test","media-test-only-password"));
        var source=new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:5544/chanter_media?currentSchema="+SCHEMA,"media_test","media-test-only-password");
        var jdbc=new JdbcTemplate(source);
        boolean restarted="true".equals(System.getenv("MEDIA_RESTART_PHASE"));
        if (!restarted) {
            // The workflow owns a fresh disposable fixture DB. Refuse an existing schema instead of replacing it.
            admin.execute("CREATE SCHEMA "+SCHEMA);
            jdbc.execute("CREATE TABLE media_storage_budget(id INT PRIMARY KEY,storage_namespace VARCHAR(64))");
            jdbc.update("INSERT INTO media_storage_budget VALUES(1,?)","a".repeat(64));
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V6__resource_recovery_maintenance.sql")).execute(source);
        }
        var store=new StorageMutationStore(jdbc,new DataSourceTransactionManager(source),Clock.systemUTC());
        if (!restarted) {
            UUID mutation=store.begin(KEY,StorageMutationStore.Operation.DELETE); store.uncertain(mutation);
            assertThat(store.fence(INVENTORY).unsettledMutations()).isEqualTo(1);
        } else {
            boolean owned=false;
            try {
                assertThat(store.receipt(INVENTORY).unsettledMutations()).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT outcome FROM media_storage_mutations WHERE object_key=?",String.class,KEY)).isEqualTo("UNKNOWN");
                owned=true;
                assertThatThrownBy(() -> store.release(INVENTORY)).hasMessageContaining("unsettled");
                assertThat(store.receipt(INVENTORY).unsettledMutations()).isEqualTo(1);
                assertThatThrownBy(() -> store.begin(KEY,StorageMutationStore.Operation.DELETE)).hasMessageContaining("maintenance");
                assertThatThrownBy(() -> store.begin(KEY,StorageMutationStore.Operation.PUT)).hasMessageContaining("maintenance");
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_storage_mutations",Integer.class)).isEqualTo(1);
            } finally { if(owned) admin.execute("DROP SCHEMA "+SCHEMA+" CASCADE"); }
        }
    }
}

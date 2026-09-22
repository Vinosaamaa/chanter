package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Owning query/transaction contracts with synthetic rows; not the real #251 canonical recovery fixture. */
class ResourceRecoveryInventoryTest {
    private JdbcTemplate jdbc;
    private StorageMutationStore mutations;
    private ResourceRecoveryInventory inventories;
    private final UUID inventory = UUID.randomUUID(), backup = UUID.randomUUID(), course = UUID.randomUUID();
    private final ResourceRecoveryInventory.Authority authority = new ResourceRecoveryInventory.Authority(1,"a".repeat(64));
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"),ZoneOffset.UTC);
    @BeforeEach void schema() {
        var source = new JdbcDataSource(); source.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE media_storage_budget(id INT PRIMARY KEY,storage_namespace VARCHAR(64))");
        jdbc.update("INSERT INTO media_storage_budget VALUES(1,?)","b".repeat(64));
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V6__resource_recovery_maintenance.sql")).execute(source);
        jdbc.execute("CREATE TABLE lifecycle_reapply_head(id INT PRIMARY KEY,revision BIGINT,digest VARCHAR(64))");
        jdbc.update("INSERT INTO lifecycle_reapply_head VALUES(1,?,?)",authority.revision(),authority.digest());
        jdbc.execute("CREATE TABLE lifecycle_terminal_targets(target_kind VARCHAR(16),target_id UUID,revision BIGINT,event_id UUID,digest VARCHAR(64))");
        jdbc.execute("CREATE TABLE lifecycle_source_requests(target_id UUID)");
        for (String prefix : java.util.List.of("lifecycle_scope_import","lifecycle_recovery_scope")) {
            jdbc.execute("CREATE TABLE "+prefix+"s(study_server_id UUID,scope_kind VARCHAR(8),revision BIGINT,event_id UUID,terminal_digest VARCHAR(64),ready BOOLEAN)");
            jdbc.execute("CREATE TABLE "+prefix+"_ids(study_server_id UUID,scope_kind VARCHAR(8),scope_id UUID)");
        }
        jdbc.execute("CREATE TABLE course_resources(id UUID PRIMARY KEY,course_id UUID,uploaded_by_user_id UUID,study_server_id UUID,storage_key VARCHAR(150),migration_key VARCHAR(150),byte_size BIGINT,sha256 VARCHAR(64),state VARCHAR(32),storage_backend VARCHAR(16),byte_reservation BOOLEAN,storage_write_settled BOOLEAN,lease_id UUID,lease_until TIMESTAMP WITH TIME ZONE)");
        var transactions = new DataSourceTransactionManager(source);
        mutations = new StorageMutationStore(jdbc,transactions,clock);
        inventories = new ResourceRecoveryInventory(jdbc,transactions,clock,true);
        mutations.fence(inventory);
    }
    private UUID resource() {
        UUID resource = UUID.randomUUID();
        jdbc.update("INSERT INTO course_resources VALUES(?,?,?,NULL,?,NULL,7,?,'QUARANTINED','s3',TRUE,TRUE,NULL,NULL)",
                resource,course,UUID.randomUUID(),key(resource),"c".repeat(64));
        return resource;
    }
    private String key(UUID resource) { return "resources/v1/"+course+"/"+resource+"/"+UUID.randomUUID(); }

    @Test void sourceSnapshotIncludesDistinctRetainedReferencesWithoutInventingProviderVersions() {
        UUID resource = resource();
        jdbc.update("UPDATE course_resources SET migration_key=? WHERE id=?",key(resource),resource);
        var captured = inventories.capture(inventory,backup,authority);
        assertThat(captured.referenceCount()).isEqualTo(2);
        assertThat(inventories.capture(inventory,backup,authority)).isEqualTo(captured);
        var first = inventories.page(inventory,authority,0,1);
        assertThat(first.references()).hasSize(1);
        assertThat(first.references().getFirst().providerVersionId()).isNull();
        assertThat(first.references().getFirst().resourceState()).isEqualTo("QUARANTINED");
        assertThat(first.references().getFirst().terminal()).isFalse();
        assertThat(first.nextAfter()).isEqualTo(1);
        assertThat(inventories.page(inventory,authority,1,1).references().getFirst().referenceKind()).isEqualTo("MIGRATION");
        assertThat(inventories.page(inventory,authority,2,1).nextAfter()).isNull();
        assertThatThrownBy(() -> mutations.release(inventory)).hasMessageContaining("Discard");
        inventories.discard(inventory);
        assertThat(mutations.receipt(inventory).inventoryId()).isEqualTo(inventory);
        mutations.release(inventory);
    }

    @Test void missingCurrentOrHistoricalScopeCannotTurnNullServerRowsIntoRestorableObjects() {
        resource(); UUID server = UUID.randomUUID(), event = UUID.randomUUID();
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('STUDY_SERVER',?,?,?,?)",server,1,event,authority.digest());
        assertThatThrownBy(() -> inventories.capture(inventory,backup,authority)).hasMessageContaining("scope is incomplete");
        scopes("lifecycle_scope_import",server,event);
        assertThatThrownBy(() -> inventories.capture(inventory,backup,authority)).hasMessageContaining("scope is incomplete");
        scopes("lifecycle_recovery_scope",server,event);
        jdbc.update("INSERT INTO lifecycle_recovery_scope_ids VALUES(?,'COURSE',?)",server,course);
        inventories.capture(inventory,backup,authority);
        assertThat(inventories.page(inventory,authority,0,256).references().getFirst().terminal()).isTrue();
    }
    private void scopes(String prefix,UUID server,UUID event) {
        for (String kind : java.util.List.of("COURSE","CHANNEL"))
            jdbc.update("INSERT INTO "+prefix+"s VALUES(?,?,1,?,?,TRUE)",server,kind,event,authority.digest());
    }

    @Test void unsettledLeaseLegacyAndUncommittedDeletionAllRefuseBeforeSnapshotWrites() {
        UUID resource = resource();
        jdbc.update("UPDATE course_resources SET storage_write_settled=FALSE");
        assertRefused("unresolved");
        jdbc.update("UPDATE course_resources SET storage_write_settled=TRUE,lease_id=?",UUID.randomUUID());
        assertRefused("unresolved");
        jdbc.update("UPDATE course_resources SET lease_id=NULL,storage_backend='legacy'");
        assertRefused("legacy");
        jdbc.update("UPDATE course_resources SET storage_backend='s3'");
        jdbc.update("INSERT INTO lifecycle_source_requests VALUES(?)",resource);
        assertRefused("uncommitted");
    }

    @Test void malformedReferenceRollsBackTheWholeSnapshot() {
        resource(); jdbc.update("UPDATE course_resources SET sha256=NULL");
        assertRefused("incomplete");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_recovery_inventory_references",Integer.class)).isZero();
    }

    @Test void laterTerminalAuthorityAndChangedNamespaceInvalidateSnapshotReads() {
        resource(); inventories.capture(inventory,backup,authority);
        jdbc.update("UPDATE lifecycle_reapply_head SET revision=2,digest=?","d".repeat(64));
        assertThatThrownBy(() -> inventories.page(inventory,authority,0,256)).hasMessageContaining("exact applied");
        jdbc.update("UPDATE lifecycle_reapply_head SET revision=1,digest=?",authority.digest());
        jdbc.update("UPDATE media_storage_budget SET storage_namespace=?","e".repeat(64));
        assertThatThrownBy(() -> inventories.page(inventory,authority,0,256)).hasMessageContaining("does not match");
    }

    @Test void individualTerminalDeliveryCannotPretendToBeACompleteAppliedPrefix() {
        UUID resource = resource();
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('RESOURCE',?,?,?,?)",resource,2,UUID.randomUUID(),"d".repeat(64));
        assertRefused("ahead");
    }
    private void assertRefused(String detail) {
        assertThatThrownBy(() -> inventories.capture(inventory,backup,authority)).hasMessageContaining(detail);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_recovery_inventory",Integer.class)).isZero();
    }
}

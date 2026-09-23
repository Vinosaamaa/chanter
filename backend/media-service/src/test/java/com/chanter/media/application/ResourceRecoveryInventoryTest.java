package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
    private JdbcTemplate nativeAdmin;
    private String nativeSchema;
    private boolean nativeSchemaCreated;
    private final UUID inventory = UUID.randomUUID(), backup = UUID.randomUUID(), course = UUID.randomUUID();
    private final UUID restoreId = UUID.randomUUID();
    private final ResourceRecoveryInventory.Authority authority = new ResourceRecoveryInventory.Authority(1,"a".repeat(64));
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"),ZoneOffset.UTC);
    @BeforeEach void schema() {
        javax.sql.DataSource source;
        if ("true".equals(System.getenv("MEDIA_INTEGRATION"))) {
            nativeSchema="recovery_342_"+UUID.randomUUID().toString().replace("-","");
            nativeAdmin=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    "jdbc:postgresql://127.0.0.1:5544/chanter_media","media_test","media-test-only-password"));
            nativeAdmin.execute("CREATE SCHEMA "+nativeSchema);
            nativeSchemaCreated=true;
            source=new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    "jdbc:postgresql://127.0.0.1:5544/chanter_media?currentSchema="+nativeSchema,"media_test","media-test-only-password");
        } else {
            var memory=new JdbcDataSource(); memory.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1"); source=memory;
        }
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE media_storage_budget(id INT PRIMARY KEY,storage_namespace VARCHAR(64))");
        jdbc.update("INSERT INTO media_storage_budget VALUES(1,?)","b".repeat(64));
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V6__resource_recovery_maintenance.sql")).execute(source);
        jdbc.execute("CREATE TABLE lifecycle_reapply_head(id INT PRIMARY KEY,revision BIGINT,digest VARCHAR(64))");
        jdbc.update("INSERT INTO lifecycle_reapply_head VALUES(1,?,?)",authority.revision(),authority.digest());
        jdbc.execute("CREATE TABLE lifecycle_terminal_targets(target_kind VARCHAR(16),target_id UUID,revision BIGINT,event_id UUID,digest VARCHAR(64))");
        jdbc.execute("CREATE TABLE lifecycle_source_requests(target_id UUID)");
        for (String prefix : java.util.List.of("lifecycle_scope_import","lifecycle_recovery_scope")) {
            jdbc.execute("CREATE TABLE "+prefix+"s(study_server_id UUID,scope_kind VARCHAR(8),revision BIGINT,event_id UUID,terminal_digest VARCHAR(64),ready BOOLEAN,scope_digest VARCHAR(64),basis_digest VARCHAR(64))");
            jdbc.execute("CREATE TABLE "+prefix+"_ids(study_server_id UUID,scope_kind VARCHAR(8),scope_id UUID)");
        }
        jdbc.execute("CREATE TABLE course_resources(id UUID PRIMARY KEY,course_id UUID,uploaded_by_user_id UUID,study_server_id UUID,storage_key VARCHAR(150),migration_key VARCHAR(150),byte_size BIGINT,sha256 VARCHAR(64),state VARCHAR(32),storage_backend VARCHAR(16),byte_reservation BOOLEAN,storage_write_settled BOOLEAN,lease_id UUID,lease_until TIMESTAMP WITH TIME ZONE)");
        var transactions = new DataSourceTransactionManager(source);
        mutations = new StorageMutationStore(jdbc,transactions,clock);
        inventories = new ResourceRecoveryInventory(jdbc,transactions,clock,mutations,true,restoreId.toString());
        mutations.fence(inventory);
    }
    @AfterEach void removeOnlyOwnedNativeFixtureSchema() {
        if (nativeSchemaCreated && nativeAdmin!=null && nativeSchema!=null && nativeSchema.matches("recovery_342_[a-f0-9]{32}"))
            nativeAdmin.execute("DROP SCHEMA "+nativeSchema+" CASCADE");
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
            jdbc.update("INSERT INTO "+prefix+"s VALUES(?,?,1,?,?,TRUE,?,?)",server,kind,event,authority.digest(),"e".repeat(64),basis(restoreId,kind,"e".repeat(64)));
    }
    private String basis(UUID restored,String kind,String original) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(("deleted-study-server-recovery-scope\n1\n"+restored+"\n"+authority.digest()+"\n"+kind+"\n"+original+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    @Test void copiedDerivedScopeFromAnotherRestoredInstanceCannotQualifyInventory() {
        resource(); UUID server=UUID.randomUUID(),event=UUID.randomUUID();
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('STUDY_SERVER',?,?,?,?)",server,1,event,authority.digest());
        scopes("lifecycle_scope_import",server,event); scopes("lifecycle_recovery_scope",server,event);
        jdbc.update("UPDATE lifecycle_recovery_scopes SET basis_digest=? WHERE scope_kind='COURSE'",basis(UUID.randomUUID(),"COURSE","e".repeat(64)));
        assertRefused("restored instance");
        assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
    }
    @Test void savedSnapshotStillRequiresCurrentArchiveAndVerifiedRuntimeIdentity() {
        resource(); UUID server=UUID.randomUUID(),event=UUID.randomUUID();
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('STUDY_SERVER',?,?,?,?)",server,1,event,authority.digest());
        scopes("lifecycle_scope_import",server,event); scopes("lifecycle_recovery_scope",server,event);
        inventories.capture(inventory,backup,authority);
        var noIdentity=new ResourceRecoveryInventory(jdbc,new DataSourceTransactionManager(jdbc.getDataSource()),clock,mutations,true,"");
        assertThatThrownBy(() -> noIdentity.page(inventory,authority,0,256)).hasMessageContaining("restored instance");
        jdbc.update("UPDATE lifecycle_scope_imports SET scope_digest=? WHERE scope_kind='COURSE'","f".repeat(64));
        assertThatThrownBy(() -> inventories.page(inventory,authority,0,256)).hasMessageContaining("archive");
        assertThatThrownBy(() -> inventories.beginRestore("s3",new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1),new byte[]{1}))
                .hasMessageContaining("archive");
        assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
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

    @Test void terminalDeletionClosureCommitsWithItsExactMutationAndSurvivesServiceRecreation() {
        UUID resource=resource();
        jdbc.update("UPDATE course_resources SET state='DELETE_PENDING' WHERE id=?",resource);
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('RESOURCE',?,?,?,?)",resource,1,UUID.randomUUID(),authority.digest());
        inventories.capture(inventory,backup,authority);
        var request=new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1);
        var deletion=inventories.beginDelete("s3",request);
        assertThat(deletion.alreadyClosed()).isFalse();
        assertThat(mutations.receipt(inventory).unsettledMutations()).isEqualTo(1);
        assertThatThrownBy(() -> inventories.completeDelete(request,UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        assertThat(mutations.receipt(inventory).unsettledMutations()).isEqualTo(1);
        inventories.completeDelete(request,deletion.mutationId());
        assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
        var restarted=new ResourceRecoveryInventory(jdbc,new DataSourceTransactionManager(jdbc.getDataSource()),clock,mutations,true,restoreId.toString());
        assertThat(restarted.beginDelete("s3",request).alreadyClosed()).isTrue();
        assertThat(mutations.receipt(inventory).inventoryId()).isEqualTo(inventory);
    }

    @Test void sourceCompletionRequiresAllRetainedKeysInsideTheOwningTransaction() {
        UUID resource=resource();String migration=key(resource);
        jdbc.update("UPDATE course_resources SET state='DELETE_PENDING',migration_key=? WHERE id=?",migration,resource);
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('RESOURCE',?,?,?,?)",resource,1,UUID.randomUUID(),authority.digest());
        inventories.capture(inventory,backup,authority);
        var first=new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1);
        var second=new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,2);
        var sourceTx=new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        var firstDelete=inventories.beginDelete("s3",first);inventories.completeDelete(first,firstDelete.mutationId());
        assertThatThrownBy(() -> sourceTx.execute(status -> inventories.requireClosedDeletionLocked(inventory,backup,authority,resource)))
                .hasMessageContaining("physical closure is incomplete");
        var secondDelete=inventories.beginDelete("s3",second);inventories.completeDelete(second,secondDelete.mutationId());
        var proof=sourceTx.execute(status -> inventories.requireClosedDeletionLocked(inventory,backup,authority,resource));
        assertThat(proof.resourceId()).isEqualTo(resource);assertThat(proof.storageBackend()).isEqualTo("s3");
        assertThat(proof.migrationKey()).isEqualTo(migration);
        assertThatThrownBy(() -> inventories.requireClosedDeletionLocked(inventory,backup,authority,resource))
                .hasMessageContaining("owning source transaction");
        jdbc.update("UPDATE course_resources SET migration_key=? WHERE id=?",key(resource),resource);
        assertThatThrownBy(() -> sourceTx.execute(status -> inventories.requireClosedDeletionLocked(inventory,backup,authority,resource)))
                .hasMessageContaining("source reference changed");
    }

    @Test void rejectedOrUnknownDeletionNeverCreatesPhysicalClosure() {
        UUID resource=resource();
        jdbc.update("UPDATE course_resources SET state='DELETE_PENDING' WHERE id=?",resource);
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('RESOURCE',?,?,?,?)",resource,1,UUID.randomUUID(),authority.digest());
        inventories.capture(inventory,backup,authority);
        var request=new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1);
        var rejected=inventories.beginDelete("s3",request);
        mutations.settled(rejected.mutationId());
        assertThatThrownBy(() -> inventories.completeDelete(request,rejected.mutationId())).isInstanceOf(IllegalStateException.class);
        var unknown=inventories.beginDelete("s3",request);
        assertThat(unknown.alreadyClosed()).isFalse();
        mutations.uncertain(unknown.mutationId());
        assertThatThrownBy(() -> inventories.beginDelete("s3",request)).hasMessageContaining("unsettled");
        assertThat(mutations.receipt(inventory).unsettledMutations()).isEqualTo(1);
    }

    @Test void authorizedRestoreOwnsOneMutationWithoutReleasingTheGlobalFence() throws Exception {
        byte[] bytes={1,2,3};
        UUID resource = resource(); exactBytes(resource,bytes); inventories.capture(inventory,backup,authority);
        var request = new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1);
        var restore = inventories.beginRestore("s3",request,bytes);
        assertThat(restore.reference().resourceId()).isEqualTo(resource);
        assertThat(mutations.receipt(inventory).unsettledMutations()).isEqualTo(1);
        assertThatThrownBy(() -> mutations.begin(key(resource),StorageMutationStore.Operation.PUT)).hasMessageContaining("maintenance");
        assertThatThrownBy(() -> inventories.beginRestore("s3",request,bytes)).hasMessageContaining("unsettled");
        mutations.settled(restore.mutationId());
        assertThat(mutations.receipt(inventory).inventoryId()).isEqualTo(inventory);
    }

    @Test void retainedButTerminalReferencesCannotAuthorizeRestore() {
        UUID resource = resource();
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('RESOURCE',?,?,?,?)",resource,1,UUID.randomUUID(),authority.digest());
        inventories.capture(inventory,backup,authority);
        assertThatThrownBy(() -> inventories.beginRestore("s3",new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1),new byte[]{1}))
                .hasMessageContaining("not restorable");
        assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
    }

    @Test void changedSourceTupleCannotAuthorizeAWrongObjectUnderAnOldSnapshot() {
        UUID resource = resource(); inventories.capture(inventory,backup,authority);
        jdbc.update("UPDATE course_resources SET storage_key=? WHERE id=?",key(resource),resource);
        assertThatThrownBy(() -> inventories.beginRestore("s3",new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1),new byte[]{1}))
                .hasMessageContaining("source reference changed");
        assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
    }
    @Test void changedStorageBackendCannotReserveRestorationFromAnOldSnapshot() throws Exception {
        byte[] bytes={1,2,3}; UUID resource=resource(); exactBytes(resource,bytes);
        inventories.capture(inventory,backup,authority);
        jdbc.update("UPDATE course_resources SET storage_backend='local' WHERE id=?",resource);
        assertThatThrownBy(() -> inventories.beginRestore("s3",new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1),bytes))
                .hasMessageContaining("source reference changed");
        assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
    }

    @Test void wrongAdapterCannotReserveRestorationForCapturedBackend() throws Exception {
        byte[] bytes={1,2,3}; UUID resource=resource(); exactBytes(resource,bytes);
        inventories.capture(inventory,backup,authority);
        assertThat(inventories.page(inventory,authority,0,1).references().getFirst().storageBackend()).isEqualTo("s3");
        assertThatThrownBy(() -> inventories.beginRestore("local",new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1),bytes))
                .hasMessageContaining("adapter does not match source backend");
        assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
    }

    @Test void invalidRecoveryBytesNeverReserveAnOperationRequiringLaterSettlement(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        byte[] bytes="expected fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        UUID resource=resource(); exactBytes(resource,bytes);
        jdbc.update("UPDATE course_resources SET storage_backend='local'");
        inventories.capture(inventory,backup,authority);
        var failedSettlement=org.mockito.Mockito.spy(mutations);
        org.mockito.Mockito.doThrow(new IllegalStateException("fixture settlement unavailable"))
                .when(failedSettlement).settled(org.mockito.ArgumentMatchers.any());
        var storage=new com.chanter.media.infra.LocalPrivateResourceStorage(directory.toString(),failedSettlement);
        storage.recoveryInventory(inventories);
        assertThatThrownBy(() -> storage.putForRecovery(new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1),new byte[]{1}))
                .isInstanceOf(PrivateResourceStorage.PutFailure.class).hasRootCauseMessage("Private recovery byte integrity mismatch");
        assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
        org.mockito.Mockito.verify(failedSettlement,org.mockito.Mockito.never()).settled(org.mockito.ArgumentMatchers.any());
    }

    @Test void actualLocalRecoveryVerifiesBytesAndNeverOverwritesOrReleasesMaintenance(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        byte[] bytes="private fixture bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        UUID resource=resource(); exactBytes(resource,bytes);
        jdbc.update("UPDATE course_resources SET storage_backend='local'");
        inventories.capture(inventory,backup,authority);
        var request=new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1);
        var storage=new com.chanter.media.infra.LocalPrivateResourceStorage(directory.toString(),mutations);
        assertThatThrownBy(() -> storage.putForRecovery(request,bytes)).hasMessageContaining("not enabled");
        storage.recoveryInventory(inventories);
        assertThatThrownBy(() -> storage.putForRecovery(request,new byte[]{1,2,3})).hasRootCauseMessage("Private recovery byte integrity mismatch");
        assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
        storage.putForRecovery(request,bytes);
        String key=inventories.page(inventory,authority,0,1).references().getFirst().key();
        try(var input=storage.open(key)) { assertThat(input.readAllBytes()).isEqualTo(bytes); }
        assertThatThrownBy(() -> storage.putForRecovery(request,bytes)).isInstanceOfSatisfying(PrivateResourceStorage.PutFailure.class,
                failure -> assertThat(failure.outcome()).isEqualTo(PrivateResourceStorage.WriteOutcome.FINISHED));
        assertThatThrownBy(() -> storage.delete(key)).hasRootCauseMessage("Storage maintenance blocks new physical operations");
        assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
        assertThat(mutations.receipt(inventory).inventoryId()).isEqualTo(inventory);
        try(var input=storage.open(key)) { assertThat(input.readAllBytes()).isEqualTo(bytes); }
    }

    @Test void actualLocalMaintenanceDeleteClosesEachReferenceWithoutReleasingFence(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        UUID resource=resource();
        jdbc.update("UPDATE course_resources SET state='DELETE_PENDING',storage_backend='local',migration_key=? WHERE id=?",key(resource),resource);
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('RESOURCE',?,?,?,?)",resource,1,UUID.randomUUID(),authority.digest());
        inventories.capture(inventory,backup,authority);
        var references=inventories.page(inventory,authority,0,256).references();
        for(var reference:references) {
            var file=directory.resolve(reference.key()); java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.write(file,new byte[]{1,2,3});
        }
        var storage=new com.chanter.media.infra.LocalPrivateResourceStorage(directory.toString(),mutations); storage.recoveryInventory(inventories);
        for(var reference:references) {
            var request=new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,reference.ordinal());
            storage.deleteForRecovery(request);
            assertThat(java.nio.file.Files.exists(directory.resolve(reference.key()))).isFalse();
            assertThat(inventories.beginDelete("local",request).alreadyClosed()).isTrue();
            storage.deleteForRecovery(request);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_recovery_inventory_references WHERE physical_closed_at IS NOT NULL",Integer.class)).isEqualTo(2);
        assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
        assertThat(mutations.receipt(inventory).inventoryId()).isEqualTo(inventory);
        assertThat(jdbc.queryForObject("SELECT byte_reservation FROM course_resources WHERE id=?",Boolean.class,resource)).isTrue();
    }

    @Test void lostPhysicalClosureTransactionRetainsMutationAndPreventsRedispatch(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        UUID resource=resource();
        jdbc.update("UPDATE course_resources SET state='DELETE_PENDING',storage_backend='local' WHERE id=?",resource);
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('RESOURCE',?,?,?,?)",resource,1,UUID.randomUUID(),authority.digest());
        inventories.capture(inventory,backup,authority);
        var request=new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1);
        var reference=inventories.page(inventory,authority,0,1).references().getFirst();
        var file=directory.resolve(reference.key());java.nio.file.Files.createDirectories(file.getParent());java.nio.file.Files.write(file,new byte[]{1});
        jdbc.execute("ALTER TABLE media_recovery_inventory_references ADD CONSTRAINT fixture_refuse_closure CHECK(physical_closed_at IS NULL)");
        var storage=new com.chanter.media.infra.LocalPrivateResourceStorage(directory.toString(),mutations);storage.recoveryInventory(inventories);
        assertThatThrownBy(() -> storage.deleteForRecovery(request)).isInstanceOfSatisfying(PrivateResourceStorage.DeleteFailure.class,
                failure -> assertThat(failure.outcome()).isEqualTo(PrivateResourceStorage.WriteOutcome.UNKNOWN));
        assertThat(java.nio.file.Files.exists(file)).isFalse();
        assertThat(mutations.receipt(inventory).unsettledMutations()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_recovery_inventory_references WHERE physical_closed_at IS NOT NULL",Integer.class)).isZero();
        assertThatThrownBy(() -> storage.deleteForRecovery(request)).hasRootCauseMessage("Inventory has unsettled physical operations");
    }

    @Test void localDeleteIoRejectionSettlesInvocationWithoutClaimingErasure(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        UUID resource=resource();jdbc.update("UPDATE course_resources SET state='DELETE_PENDING',storage_backend='local' WHERE id=?",resource);
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('RESOURCE',?,?,?,?)",resource,1,UUID.randomUUID(),authority.digest());
        inventories.capture(inventory,backup,authority);
        var reference=inventories.page(inventory,authority,0,1).references().getFirst();
        var target=directory.resolve(reference.key());java.nio.file.Files.createDirectories(target);java.nio.file.Files.write(target.resolve("fixture"),new byte[]{1});
        var storage=new com.chanter.media.infra.LocalPrivateResourceStorage(directory.toString(),mutations);storage.recoveryInventory(inventories);
        assertThatThrownBy(() -> storage.deleteForRecovery(new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1)))
                .isInstanceOfSatisfying(PrivateResourceStorage.DeleteFailure.class,
                        failure -> assertThat(failure.outcome()).isEqualTo(PrivateResourceStorage.WriteOutcome.FINISHED));
        assertThat(java.nio.file.Files.exists(target.resolve("fixture"))).isTrue();
        assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_recovery_inventory_references WHERE physical_closed_at IS NOT NULL",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT byte_reservation FROM course_resources WHERE id=?",Boolean.class,resource)).isTrue();
    }

    @Test void liveResourceCannotAuthorizePhysicalDeletion() {
        resource(); inventories.capture(inventory,backup,authority);
        assertThatThrownBy(() -> inventories.beginDelete("s3",new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1)))
                .hasMessageContaining("no terminal authority");
        assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
    }

    @Test void versionlessRemoteClosureRefusesRetainedVersionsAndRequiresDefinitiveAbsence() throws Exception {
        UUID resource=resource();jdbc.update("UPDATE course_resources SET state='DELETE_PENDING' WHERE id=?",resource);
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('RESOURCE',?,?,?,?)",resource,1,UUID.randomUUID(),authority.digest());
        inventories.capture(inventory,backup,authority);
        var request=new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1);
        var versioning=new java.util.concurrent.atomic.AtomicReference<>("Enabled");var deletes=new java.util.concurrent.atomic.AtomicInteger();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            if(exchange.getRequestMethod().equals("GET") && "versioning".equals(exchange.getRequestURI().getQuery())) {
                String status=versioning.get();
                byte[] xml=("<VersioningConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
                        +(status.isEmpty() ? "" : "<Status>"+status+"</Status>")+"</VersioningConfiguration>").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,xml.length);try(var body=exchange.getResponseBody()){body.write(xml);}
            } else {deletes.incrementAndGet();exchange.sendResponseHeaders(404,-1);exchange.close();}
        });server.start();
        var storage=new com.chanter.media.infra.S3PrivateResourceStorage(org.mockito.Mockito.mock(ResourceLifecycle.class),mutations,
                "http://127.0.0.1:"+server.getAddress().getPort(),"us-east-1","fixture-bucket","fixture-key","fixture-secret",true);
        try {
            storage.recoveryInventory(inventories);
            for(String status:java.util.List.of("Enabled","Suspended")) {
                versioning.set(status);
                assertThatThrownBy(() -> storage.deleteForRecovery(request)).isInstanceOfSatisfying(PrivateResourceStorage.DeleteFailure.class,
                        failure -> assertThat(failure.outcome()).isEqualTo(PrivateResourceStorage.WriteOutcome.NOT_STARTED));
                assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
            }
            assertThat(deletes.get()).isZero();versioning.set("");
            storage.deleteForRecovery(request);
            assertThat(inventories.beginDelete("s3",request).alreadyClosed()).isTrue();
            assertThat(deletes.get()).isEqualTo(1);
        } finally {storage.close();server.stop(0);}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"x-amz-delete-marker","x-amz-version-id"})
    void remoteVersionEvidenceRetainsUncertaintyAfterSuccessfulStatus(String header) throws Exception {
        UUID resource=resource();jdbc.update("UPDATE course_resources SET state='DELETE_PENDING' WHERE id=?",resource);
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('RESOURCE',?,?,?,?)",resource,1,UUID.randomUUID(),authority.digest());
        inventories.capture(inventory,backup,authority);
        var request=new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1);
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            if(exchange.getRequestMethod().equals("GET")) {
                byte[] xml="<VersioningConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,xml.length);try(var body=exchange.getResponseBody()){body.write(xml);}
            } else {exchange.getResponseHeaders().add(header,header.equals("x-amz-delete-marker") ? "true" : "fixture-version");exchange.sendResponseHeaders(204,-1);exchange.close();}
        });server.start();
        var storage=new com.chanter.media.infra.S3PrivateResourceStorage(org.mockito.Mockito.mock(ResourceLifecycle.class),mutations,
                "http://127.0.0.1:"+server.getAddress().getPort(),"us-east-1","fixture-bucket","fixture-key","fixture-secret",true);
        try {
            storage.recoveryInventory(inventories);
            assertThatThrownBy(() -> storage.deleteForRecovery(request)).isInstanceOfSatisfying(PrivateResourceStorage.DeleteFailure.class,
                    failure -> assertThat(failure.outcome()).isEqualTo(PrivateResourceStorage.WriteOutcome.UNKNOWN));
            assertThat(mutations.receipt(inventory).unsettledMutations()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_recovery_inventory_references WHERE physical_closed_at IS NOT NULL",Integer.class)).isZero();
        } finally {storage.close();server.stop(0);}
    }

    @Test void remoteDeleteRejectionAndAmbiguousCompletionDoNotClaimPhysicalClosure() throws Exception {
        UUID resource=resource();jdbc.update("UPDATE course_resources SET state='DELETE_PENDING' WHERE id=?",resource);
        jdbc.update("INSERT INTO lifecycle_terminal_targets VALUES('RESOURCE',?,?,?,?)",resource,1,UUID.randomUUID(),authority.digest());
        inventories.capture(inventory,backup,authority);
        var request=new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1);
        var response=new java.util.concurrent.atomic.AtomicInteger(403); var calls=new java.util.concurrent.atomic.AtomicInteger();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            if(exchange.getRequestMethod().equals("GET") && "versioning".equals(exchange.getRequestURI().getQuery())) {
                byte[] xml="<VersioningConfiguration xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\"/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,xml.length);try(var body=exchange.getResponseBody()){body.write(xml);}
            } else {calls.incrementAndGet();exchange.sendResponseHeaders(response.get(),-1);exchange.close();}
        });server.start();
        var storage=new com.chanter.media.infra.S3PrivateResourceStorage(org.mockito.Mockito.mock(ResourceLifecycle.class),mutations,
                "http://127.0.0.1:"+server.getAddress().getPort(),"us-east-1","fixture-bucket","fixture-key","fixture-secret",true);
        try {
            storage.recoveryInventory(inventories);
            assertThatThrownBy(() -> storage.deleteForRecovery(request)).isInstanceOfSatisfying(PrivateResourceStorage.DeleteFailure.class,
                    failure -> assertThat(failure.outcome()).isEqualTo(PrivateResourceStorage.WriteOutcome.FINISHED));
            assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
            response.set(500);
            assertThatThrownBy(() -> storage.deleteForRecovery(request)).isInstanceOfSatisfying(PrivateResourceStorage.DeleteFailure.class,
                    failure -> assertThat(failure.outcome()).isEqualTo(PrivateResourceStorage.WriteOutcome.UNKNOWN));
            assertThat(mutations.receipt(inventory).unsettledMutations()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM media_recovery_inventory_references WHERE physical_closed_at IS NOT NULL",Integer.class)).isZero();
            assertThatThrownBy(() -> storage.deleteForRecovery(request)).hasRootCauseMessage("Inventory has unsettled physical operations");
            assertThat(calls.get()).isEqualTo(2);
        } finally { storage.close(); server.stop(0); }
    }

    @Test void actualS3RecoveryUsesConditionalCreationAndRetainsUnknownCompletion() throws Exception {
        byte[] bytes="private fixture bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        UUID resource=resource(); exactBytes(resource,bytes); inventories.capture(inventory,backup,authority);
        var request=new ResourceRecoveryInventory.RestoreRequest(inventory,backup,authority,1);
        var observed=new java.util.concurrent.atomic.AtomicReference<byte[]>();
        var puts=new java.util.concurrent.atomic.AtomicInteger(); var conditional=new java.util.concurrent.atomic.AtomicBoolean();
        var response=new java.util.concurrent.atomic.AtomicInteger(200);
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            if(exchange.getRequestMethod().equals("PUT")) {
                puts.incrementAndGet(); conditional.set("*".equals(exchange.getRequestHeaders().getFirst("If-None-Match")));
                byte[] uploaded=exchange.getRequestBody().readAllBytes();
                int status=observed.compareAndSet(null,uploaded) ? response.get() : 412;
                exchange.sendResponseHeaders(status,-1); exchange.close();
            } else {
                byte[] saved=observed.get(); exchange.sendResponseHeaders(200,saved.length);
                try(var body=exchange.getResponseBody()) { body.write(saved); }
            }
        }); server.start();
        var storage=new com.chanter.media.infra.S3PrivateResourceStorage(org.mockito.Mockito.mock(ResourceLifecycle.class),mutations,
                "http://127.0.0.1:"+server.getAddress().getPort(),"us-east-1","fixture-bucket","fixture-key","fixture-secret",true);
        try {
            storage.recoveryInventory(inventories);
            storage.putForRecovery(request,bytes);
            String key=inventories.page(inventory,authority,0,1).references().getFirst().key();
            try(var input=storage.open(key)) { assertThat(input.readAllBytes()).isEqualTo(bytes); }
            assertThatThrownBy(() -> storage.putForRecovery(request,bytes)).isInstanceOfSatisfying(PrivateResourceStorage.PutFailure.class,
                    failure -> assertThat(failure.outcome()).isEqualTo(PrivateResourceStorage.WriteOutcome.FINISHED));
            assertThat(observed.get()).isEqualTo(bytes);
            assertThat(mutations.receipt(inventory).unsettledMutations()).isZero();
            // Inject missing destination bytes, then a lost completion response. This is not writer-closure evidence.
            observed.set(null);
            response.set(500);
            assertThatThrownBy(() -> storage.putForRecovery(request,bytes)).isInstanceOfSatisfying(PrivateResourceStorage.PutFailure.class,
                    failure -> assertThat(failure.outcome()).isEqualTo(PrivateResourceStorage.WriteOutcome.UNKNOWN));
            assertThat(conditional.get()).isTrue(); assertThat(observed.get()).isEqualTo(bytes);
            assertThat(mutations.receipt(inventory).unsettledMutations()).isEqualTo(1);
            assertThatThrownBy(() -> storage.putForRecovery(request,bytes)).hasRootCauseMessage("Inventory has unsettled physical operations");
            assertThat(puts.get()).isEqualTo(3);
            assertThat(mutations.receipt(inventory).inventoryId()).isEqualTo(inventory);
        } finally { storage.close(); server.stop(0); }
    }
    private void exactBytes(UUID resource,byte[] bytes) throws Exception {
        String checksum=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        jdbc.update("UPDATE course_resources SET byte_size=?,sha256=? WHERE id=?",bytes.length,checksum,resource);
    }
}

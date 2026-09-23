package com.chanter.media.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Temporary source-owned catalogue snapshot. Neither provider closure nor byte verification is implied. */
@Repository
@ConditionalOnProperty(name = {"chanter.media.recovery-inventory-enabled", "chanter.recovery-mode"}, havingValue = "true")
public class ResourceRecoveryInventory {
    private static final int MAX_REFERENCES = 250_000;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final boolean recovery;
    private final StorageMutationStore mutations;
    public record Authority(long revision, String digest) {
        public Authority {
            if (revision < 0 || digest == null || !digest.matches("[a-f0-9]{64}")
                    || (revision == 0) != digest.equals("0".repeat(64))) throw new IllegalArgumentException("Invalid inventory authority");
        }
    }
    public record Snapshot(int schemaVersion, UUID inventoryId, UUID databaseBackupId, Authority authority,
                           String namespaceSha256, Instant capturedAt, int referenceCount, String referenceDigest) { }
    public record Reference(int ordinal, UUID resourceId, UUID courseId, String referenceKind, String key, long byteSize,
                            String sha256, String resourceState, boolean sourceRetained, boolean terminal,
                            String providerVersionId) { }
    public record Page(int schemaVersion, Snapshot snapshot, int after, List<Reference> references, Integer nextAfter) { }
    public record RestoreRequest(UUID inventoryId, UUID databaseBackupId, Authority authority, int ordinal) { }
    public record RestoreMutation(UUID mutationId, Reference reference) { }

    public ResourceRecoveryInventory(JdbcTemplate jdbc, PlatformTransactionManager transactions, Clock clock, StorageMutationStore mutations,
            @Value("${chanter.recovery-mode:false}") boolean recovery) {
        this.jdbc = jdbc; this.clock = clock; this.recovery = recovery; this.mutations = mutations;
        tx = new TransactionTemplate(transactions); tx.setTimeout(30);
    }

    public RestoreMutation beginRestore(RestoreRequest request, byte[] content) {
        if (content==null || content.length<1 || content.length>10*1024*1024) throw new IllegalArgumentException("Invalid recovery byte length");
        java.util.Objects.requireNonNull(request); requireIdentity(request.inventoryId()); requireIdentity(request.databaseBackupId());
        java.util.Objects.requireNonNull(request.authority());
        if (request.ordinal()<1 || request.ordinal()>MAX_REFERENCES) throw new IllegalArgumentException("Invalid restore reference");
        requireOutsideTransaction();
        return tx.execute(status -> {
            String namespace=qualify(request.inventoryId(),request.authority());
            Snapshot snapshot=saved();
            if (snapshot==null || !snapshot.inventoryId().equals(request.inventoryId()) || !snapshot.databaseBackupId().equals(request.databaseBackupId())
                    || !snapshot.authority().equals(request.authority()) || !snapshot.namespaceSha256().equals(namespace))
                throw new IllegalStateException("Restore inventory snapshot does not match");
            var references=jdbc.query("SELECT * FROM media_recovery_inventory_references WHERE inventory_id=? AND ordinal=?",
                    (rs,n) -> reference(rs),request.inventoryId(),request.ordinal());
            if (references.size()!=1) throw new IllegalStateException("Restore reference is missing");
            Reference reference=references.getFirst();
            if (reference.terminal() || !reference.sourceRetained()
                    || !java.util.Set.of("AVAILABLE","QUARANTINED","SCAN_FAILED").contains(reference.resourceState()))
                throw new IllegalStateException("Inventory reference is not restorable");
            if (jdbc.queryForObject("SELECT COUNT(*) FROM course_resources WHERE id=? AND course_id=? AND sha256=? AND byte_size=?"
                            + " AND state=? AND byte_reservation=TRUE AND " + (reference.referenceKind().equals("CURRENT") ? "storage_key=?" : "migration_key=?"),
                    Integer.class,reference.resourceId(),reference.courseId(),reference.sha256(),reference.byteSize(),reference.resourceState(),reference.key())!=1)
                throw new IllegalStateException("Restore source reference changed");
            // Validate the adapter's bounded private copy before committing an operation that needs settlement.
            try { PrivateResourceStorage.verifyRecoveryBytes(reference,content); }
            catch (java.io.IOException invalid) { throw new IllegalArgumentException("Invalid recovery bytes",invalid); }
            return new RestoreMutation(mutations.beginRecoveryPutLocked(request.inventoryId(),reference.key()),reference);
        });
    }

    public Snapshot capture(UUID inventory, UUID databaseBackup, Authority authority) {
        requireIdentity(inventory); requireIdentity(databaseBackup); java.util.Objects.requireNonNull(authority);
        requireOutsideTransaction();
        return tx.execute(status -> {
            String namespace = qualify(inventory, authority);
            var existing = saved();
            if (existing != null) {
                if (!existing.inventoryId().equals(inventory) || !existing.databaseBackupId().equals(databaseBackup)
                        || !existing.authority().equals(authority) || !existing.namespaceSha256().equals(namespace)) throw new IllegalStateException("Inventory snapshot identity changed");
                return existing;
            }
            long count = jdbc.queryForObject("SELECT COUNT(*) + COALESCE(SUM(CASE WHEN migration_key IS NOT NULL AND migration_key<>storage_key THEN 1 ELSE 0 END),0) FROM course_resources", Long.class);
            if (count > MAX_REFERENCES) throw new IllegalStateException("Inventory reference capacity exceeded");
            Instant captured = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            String initial = hash("resource-recovery-inventory\n1\n" + inventory + "\n" + databaseBackup + "\n"
                    + authority.revision() + "\n" + authority.digest() + "\n" + namespace + "\n");
            jdbc.update("INSERT INTO media_recovery_inventory VALUES(1,?,?,?,?,?,?,0,?)", inventory, databaseBackup,
                    authority.revision(), authority.digest(), namespace, Timestamp.from(captured), initial);
            var pending = new ArrayList<Object[]>();
            var digest = new String[] {initial}; var ordinal = new int[] {0};
            String terminal = "EXISTS(SELECT 1 FROM lifecycle_terminal_targets t WHERE (t.target_kind='RESOURCE' AND t.target_id=r.id)"
                    + " OR (t.target_kind='ACCOUNT' AND t.target_id=r.uploaded_by_user_id)"
                    + " OR (t.target_kind='STUDY_SERVER' AND t.target_id=r.study_server_id))"
                    + " OR " + scoped("lifecycle_scope_import") + " OR " + scoped("lifecycle_recovery_scope");
            jdbc.query(connection -> {
                var query = connection.prepareStatement("SELECT r.*, (" + terminal + ") AS terminal FROM course_resources r ORDER BY CAST(r.id AS VARCHAR)");
                query.setFetchSize(256); return query;
            }, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                UUID resource = rs.getObject("id", UUID.class), course = rs.getObject("course_id", UUID.class);
                String key = rs.getString("storage_key"), migration = rs.getString("migration_key");
                for (String referenceKey : migration != null && !migration.equals(key) ? List.of(key, migration) : List.of(key)) {
                    String kind = referenceKey.equals(key) ? "CURRENT" : "MIGRATION";
                    long bytes = rs.getLong("byte_size"); String checksum = rs.getString("sha256");
                    validateReference(referenceKey, course, resource, bytes, checksum);
                    int position = ++ordinal[0];
                    if (position > MAX_REFERENCES) throw new IllegalStateException("Inventory reference capacity exceeded");
                    String state = rs.getString("state"); boolean retained = rs.getBoolean("byte_reservation"), deleted = rs.getBoolean("terminal");
                    if (!java.util.Set.of("AVAILABLE","QUARANTINED","SCAN_FAILED","REJECTED","DELETE_PENDING","DELETED").contains(state))
                        throw new IllegalStateException("Inventory resource state is unsupported");
                    digest[0] = hash(digest[0] + "\n" + resource + "\n" + kind + "\n" + referenceKey + "\n"
                            + bytes + "\n" + checksum + "\n" + state + "\n" + retained + "\n" + deleted + "\n");
                    pending.add(new Object[]{inventory, position, resource, course, kind, referenceKey, bytes, checksum, state, retained, deleted});
                    if (pending.size() == 256) flush(pending);
                }
            });
            flush(pending);
            if (ordinal[0] != count) throw new IllegalStateException("Inventory source changed during capture");
            jdbc.update("UPDATE media_recovery_inventory SET reference_count=?,reference_digest=? WHERE id=1", ordinal[0], digest[0]);
            return saved();
        });
    }

    public Page page(UUID inventory, Authority authority, int after, int limit) {
        requireIdentity(inventory); java.util.Objects.requireNonNull(authority);
        if (after < 0 || limit < 1 || limit > 256) throw new IllegalArgumentException("Invalid inventory page bounds");
        requireOutsideTransaction();
        return tx.execute(status -> {
            String namespace = qualify(inventory, authority);
            Snapshot snapshot = saved();
            if (snapshot == null || !snapshot.inventoryId().equals(inventory) || !snapshot.authority().equals(authority)
                    || !snapshot.namespaceSha256().equals(namespace) || after > snapshot.referenceCount()) throw new IllegalStateException("Inventory snapshot does not match");
            var rows = jdbc.query("SELECT * FROM media_recovery_inventory_references WHERE inventory_id=? AND ordinal>? ORDER BY ordinal LIMIT ?",
                    (rs,n) -> reference(rs),inventory,after,limit);
            int last = rows.isEmpty() ? after : rows.getLast().ordinal();
            return new Page(1,snapshot,after,List.copyOf(rows),last < snapshot.referenceCount() ? last : null);
        });
    }

    /** Discarding an unfinished local snapshot never releases its physical maintenance fence. */
    public void discard(UUID inventory) {
        requireIdentity(inventory); requireOutsideTransaction();
        tx.executeWithoutResult(status -> {
            UUID active = jdbc.queryForObject("SELECT maintenance_inventory_id FROM media_storage_budget WHERE id=1 FOR UPDATE", UUID.class);
            if (!inventory.equals(active)) throw new IllegalStateException("Inventory maintenance identity changed");
            jdbc.update("DELETE FROM media_recovery_inventory_references WHERE inventory_id=?", inventory);
            jdbc.update("DELETE FROM media_recovery_inventory WHERE inventory_id=?", inventory);
        });
    }

    private String qualify(UUID inventory, Authority authority) {
        // #251 owns this lock and schema. Missing source integration fails closed before any snapshot writes.
        Authority current = jdbc.queryForObject("SELECT revision,digest FROM lifecycle_reapply_head WHERE id=1 FOR UPDATE",
                (rs,n) -> new Authority(rs.getLong(1),rs.getString(2)));
        if (!authority.equals(current)) throw new IllegalStateException("Inventory requires the exact applied terminal prefix");
        String namespace = jdbc.queryForObject("SELECT maintenance_inventory_id,storage_namespace FROM media_storage_budget WHERE id=1 FOR UPDATE", (rs,n) -> {
            if (!inventory.equals(rs.getObject(1,UUID.class))) throw new IllegalStateException("Inventory maintenance identity changed");
            return rs.getString(2);
        });
        if (namespace == null || !namespace.matches("[a-f0-9]{64}")) throw new IllegalStateException("Inventory storage namespace is unknown");
        if (count("SELECT COUNT(*) FROM media_storage_mutations") != 0) throw new IllegalStateException("Inventory has unsettled physical operations");
        if (count("SELECT COUNT(*) FROM lifecycle_terminal_targets WHERE revision>" + authority.revision()) != 0)
            throw new IllegalStateException("Inventory terminal delivery is ahead of the applied prefix");
        if (count("SELECT COUNT(*) FROM lifecycle_source_requests r WHERE NOT EXISTS(SELECT 1 FROM lifecycle_terminal_targets t WHERE t.target_kind='RESOURCE' AND t.target_id=r.target_id)") != 0)
            throw new IllegalStateException("Inventory has uncommitted source deletion authority");
        if (count("SELECT COUNT(*) FROM course_resources WHERE storage_write_settled=FALSE OR lease_id IS NOT NULL OR lease_until IS NOT NULL OR state IN ('STAGING','SCANNING','LEGACY') OR storage_backend NOT IN ('local','s3')") != 0)
            throw new IllegalStateException("Inventory has unresolved source writes, leases or legacy references");
        requireScopes("lifecycle_scope_imports");
        if (recovery) requireScopes("lifecycle_recovery_scopes");
        return namespace;
    }
    private void requireScopes(String table) {
        if (count("SELECT COUNT(*) FROM lifecycle_terminal_targets t WHERE t.target_kind='STUDY_SERVER' AND"
                + " (SELECT COUNT(*) FROM " + table + " s WHERE s.study_server_id=t.target_id AND s.revision=t.revision"
                + " AND s.event_id=t.event_id AND s.terminal_digest=t.digest AND s.ready=TRUE AND s.scope_kind IN ('COURSE','CHANNEL'))<>2") != 0)
            throw new IllegalStateException("Inventory terminal Study Server scope is incomplete");
    }
    private static String scoped(String table) {
        return "EXISTS(SELECT 1 FROM " + table + "_ids s JOIN " + (table.equals("lifecycle_scope_import") ? "lifecycle_scope_imports" : "lifecycle_recovery_scopes")
                + " h ON h.study_server_id=s.study_server_id AND h.scope_kind=s.scope_kind JOIN lifecycle_terminal_targets t"
                + " ON t.target_kind='STUDY_SERVER' AND t.target_id=h.study_server_id AND t.revision=h.revision"
                + " AND t.event_id=h.event_id AND t.digest=h.terminal_digest WHERE h.ready=TRUE AND s.scope_kind='COURSE' AND s.scope_id=r.course_id)";
    }
    private void flush(ArrayList<Object[]> rows) {
        if (!rows.isEmpty()) { jdbc.batchUpdate("INSERT INTO media_recovery_inventory_references VALUES(?,?,?,?,?,?,?,?,?,?,?)", rows); rows.clear(); }
    }
    private Snapshot saved() {
        return jdbc.query("SELECT * FROM media_recovery_inventory WHERE id=1", (rs,n) -> new Snapshot(1,
                rs.getObject("inventory_id",UUID.class),rs.getObject("database_backup_id",UUID.class),
                new Authority(rs.getLong("authority_revision"),rs.getString("authority_digest")),rs.getString("namespace_sha256"),
                rs.getTimestamp("captured_at").toInstant(),rs.getInt("reference_count"),rs.getString("reference_digest")))
                .stream().findFirst().orElse(null);
    }
    private int count(String sql) { return jdbc.queryForObject(sql,Integer.class); }
    private static Reference reference(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Reference(rs.getInt("ordinal"),rs.getObject("resource_id",UUID.class),rs.getObject("course_id",UUID.class),rs.getString("reference_kind"),
                rs.getString("object_key"),rs.getLong("byte_size"),rs.getString("sha256"),rs.getString("resource_state"),
                rs.getBoolean("source_retained"),rs.getBoolean("terminal"),null);
    }
    private static void validateReference(String key, UUID course, UUID resource, long size, String digest) {
        requireIdentity(course); requireIdentity(resource);
        PrivateResourceStorage.requireKey(key);
        String[] parts = key.split("/");
        if (!course.toString().equals(parts[2]) || !resource.toString().equals(parts[3])
                || !UUID.fromString(parts[4]).toString().equals(parts[4]) || UUID.fromString(parts[4]).equals(new UUID(0,0))
                || size < 1 || size > 10 * 1024 * 1024 || digest == null || !digest.matches("[a-f0-9]{64}"))
            throw new IllegalStateException("Inventory object reference is incomplete");
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void requireIdentity(UUID id) {
        if (id == null || id.equals(new UUID(0,0))) throw new IllegalArgumentException("Invalid inventory identity");
    }
    private static void requireOutsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Inventory must own its source transaction");
    }
}

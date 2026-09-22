package com.chanter.common.lifecycle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Public wire format for private recovery replication. No identity, content or credentials are journaled. */
public final class TerminalJournal {
    public static final int SCHEMA_VERSION = 2;
    public static final String RETENTION_POLICY = "PRESERVE_MODERATION_RECORDS_V1";
    public static final String GENESIS = "0".repeat(64);
    public static final int MAX_PAGE = 500;
    private static final Set<String> TARGETS = Set.of("ACCOUNT", "STUDY_SERVER", "RESOURCE");
    private TerminalJournal() { }

    public static void requireTarget(String kind, UUID id) {
        if (!TARGETS.contains(kind == null ? "" : kind) || id == null || id.equals(new UUID(0, 0)))
            throw new IllegalArgumentException("Invalid terminal target");
    }

    public static String digest(long revision, UUID eventId, String kind, UUID targetId, Instant deletedAt, String previous) {
        requireTarget(kind, targetId);
        if (revision < 1 || eventId == null || deletedAt == null || !deletedAt.equals(deletedAt.truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
                || previous == null || !previous.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid journal entry");
        String canonical = "2\n" + revision + "\n" + eventId + "\n" + kind + "\n" + targetId + "\nDELETE\n" + deletedAt + "\n" + RETENTION_POLICY + "\n" + previous + "\n";
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public record Entry(long revision, UUID eventId, String targetKind, UUID targetId, String action, Instant deletedAt,
                        String retentionPolicy, String previousDigest, String digest) {
        public void validate() {
            if (!RETENTION_POLICY.equals(retentionPolicy) || !"DELETE".equals(action)
                    || !TerminalJournal.digest(revision, eventId, targetKind, targetId, deletedAt, previousDigest).equals(digest))
                throw new IllegalArgumentException("Invalid journal digest");
        }
    }
    public record Watermark(long revision, String digest) {
        public void validate() {
            if (revision < 0 || digest == null || !digest.matches("[a-f0-9]{64}") || revision == 0 && !GENESIS.equals(digest))
                throw new IllegalArgumentException("Invalid journal watermark");
        }
    }
    public record Page(int schemaVersion, Watermark after, Watermark through, List<Entry> entries, Watermark next) {
        public Page { entries = List.copyOf(entries); }
        public void validate() {
            if (schemaVersion != SCHEMA_VERSION || after == null || through == null || next == null || entries.size() > MAX_PAGE)
                throw new IllegalArgumentException("Invalid journal page");
            after.validate(); through.validate(); next.validate();
            if (after.revision() > through.revision() || next.revision() > through.revision()) throw new IllegalArgumentException("Invalid journal range");
            Watermark cursor = after;
            for (Entry entry : entries) {
                entry.validate();
                if (entry.revision() != cursor.revision() + 1 || !entry.previousDigest().equals(cursor.digest()))
                    throw new IllegalArgumentException("Discontinuous journal page");
                cursor = new Watermark(entry.revision(), entry.digest());
            }
            if (!cursor.equals(next) || entries.isEmpty() && after.revision() < through.revision()
                    || next.revision() == through.revision() && !next.equals(through))
                throw new IllegalArgumentException("Incomplete journal page");
        }
    }
    public record Checkpoint(long revision, String digest, UUID checkpointId) {
        public void validate() {
            new Watermark(revision, digest).validate();
            if (checkpointId == null || checkpointId.equals(new UUID(0, 0))) throw new IllegalArgumentException("Invalid checkpoint identity");
        }
    }
}

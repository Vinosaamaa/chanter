package com.chanter.common.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** A failed page, revoked access or digest mismatch never receives a completed ZIP central directory. */
public final class ExportArchive {
    private ExportArchive() { }
    public static void write(OutputStream output, List<ExportSnapshotStore.Manifest> manifests, ObjectMapper mapper,
            PageReader pages, Runnable requireAccess) throws IOException {
        if (manifests.size() != AccountExportProtocol.SOURCES.size()
                || !manifests.stream().map(ExportSnapshotStore.Manifest::source).collect(java.util.stream.Collectors.toSet()).equals(AccountExportProtocol.SOURCES))
            throw new IllegalArgumentException("Incomplete export source set");
        var first = manifests.getFirst();
        for (var manifest : manifests) {
            manifest.fingerprint();
            if (!manifest.jobId().equals(first.jobId()) || !manifest.accountId().equals(first.accountId()) || !manifest.expiresAt().equals(first.expiresAt()))
                throw new IllegalArgumentException("Export archive scope mismatch");
        }
        var zip = new ArchiveZip(output);
        try {
            requireAccess.run();
            var root = Map.of("schemaVersion", 1, "jobId", first.jobId(), "expiresAt", first.expiresAt(), "sources", manifests,
                    "consistency", "Separate source snapshots; capture times may differ.",
                    "omissions", List.of("Credentials and operator-only evidence are excluded.",
                    "Analytics and realtime have no independent durable personal database in this deployment.",
                    "Each source coverage section explains omissions and access restrictions."));
            zip.putNextEntry(entry("manifest.json"));
            zip.write(mapper.writeValueAsBytes(root)); zip.closeEntry();
            for (var manifest : manifests) for (var entry : manifest.entries()) {
                requireAccess.run();
                zip.putNextEntry(entry(manifest.source() + "/" + entry.path()));
                MessageDigest digest = sha256(); long count = 0;
                for (int page = 0; page < entry.pageCount(); page++) {
                    requireAccess.run();
                    byte[] bytes = pages.read(manifest, entry.ordinal(), page);
                    int expected = (int) Math.min(ExportSnapshotStore.PAGE_BYTES, entry.bytes() - count);
                    if (bytes == null || bytes.length != expected) throw new IOException("EXPORT_PAGE_INTEGRITY_FAILURE");
                    requireAccess.run();
                    digest.update(bytes); count += bytes.length; zip.write(bytes);
                }
                if (count != entry.bytes() || !HexFormat.of().formatHex(digest.digest()).equals(entry.sha256()))
                    throw new IOException("EXPORT_ENTRY_INTEGRITY_FAILURE");
                zip.closeEntry();
            }
            requireAccess.run();
            zip.finish();
        } finally {
            // close() would finalize a partial archive during exception unwinding. Release only the compressor.
            zip.release();
        }
    }
    private static ZipEntry entry(String path) { var entry = new ZipEntry(path); entry.setTime(0); return entry; }
    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    @FunctionalInterface public interface PageReader {
        byte[] read(ExportSnapshotStore.Manifest manifest, int entry, int page) throws IOException;
    }
    private static final class ArchiveZip extends ZipOutputStream {
        ArchiveZip(OutputStream output) { super(output, StandardCharsets.UTF_8); setLevel(1); }
        void release() { def.end(); }
    }
}

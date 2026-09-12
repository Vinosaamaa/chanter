package com.chanter.media.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UploadValidator {
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("txt", "text/plain"), Map.entry("md", "text/markdown"), Map.entry("markdown", "text/markdown"),
            Map.entry("pdf", "application/pdf"), Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("m4a", "audio/mp4"), Map.entry("wav", "audio/wav"),
            Map.entry("ogg", "audio/ogg"), Map.entry("mp4", "video/mp4"), Map.entry("webm", "video/webm"),
            Map.entry("mov", "video/quicktime"));
    private final Path spool;
    private final long limit;

    public UploadValidator(@Value("${chanter.media.spool-dir:./data/media-spool}") String directory,
                           @Value("${chanter.media.max-file-bytes:10485760}") long limit) throws IOException {
        if (limit < 1 || limit > 10L * 1024 * 1024) throw new IllegalArgumentException("Invalid resource size limit");
        this.spool = Path.of(directory).toAbsolutePath().normalize();
        Files.createDirectories(spool);
        if (Files.getFileStore(spool).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(spool, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
        this.limit = limit;
    }

    public ValidatedUpload validate(MultipartFile file, String expectedChecksum) {
        if (file == null || file.isEmpty()) throw bad("Course Resource must not be empty");
        if (file.getSize() > limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Course Resource exceeds the size limit");
        String filename = filename(file.getOriginalFilename());
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String expected = TYPES.get(extension);
        String declared = file.getContentType() == null ? "" : file.getContentType().split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        if (expected == null || !compatible(expected, declared)) throw bad("Course Resource type is not allowed");
        if (expectedChecksum != null && !expectedChecksum.matches("[a-fA-F0-9]{64}")) throw bad("Invalid SHA-256 checksum");
        Path temporary = null;
        try {
            temporary = Files.createTempFile(spool, "upload-", ".part");
            long size = copyBounded(file.getInputStream(), temporary, limit);
            if (size == 0) throw bad("Course Resource must not be empty");
            String detected;
            try (var input = TikaInputStream.get(temporary)) {
                detected = new DefaultDetector().detect(input, new Metadata(), new org.apache.tika.parser.ParseContext()).toString();
            }
            if (expected.equals(TYPES.get("pptx"))) {
                if (!detected.contains("zip") && !detected.contains("ooxml")) throw bad("File bytes do not match the file type");
                verifyPresentation(temporary);
            } else if (expected.startsWith("text/")) {
                if (!detected.equals("text/plain")) throw bad("File bytes do not match the file type");
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(Files.readAllBytes(temporary))).toString();
                if (text.chars().anyMatch(c -> (c < 32 && c != 9 && c != 10 && c != 13) || c == 127)) throw bad("Text contains unsupported control bytes");
            } else if (!compatible(expected, detected)) throw bad("File bytes do not match the file type");
            if (extension.equals("pdf")) {
                byte[] bytes = Files.readAllBytes(temporary);
                String end = new String(bytes, Math.max(0, bytes.length - 1024), Math.min(1024, bytes.length), StandardCharsets.ISO_8859_1);
                if (!end.contains("%%EOF")) throw bad("PDF is incomplete");
            }
            String checksum = checksum(temporary);
            if (expectedChecksum != null && !MessageDigest.isEqual(checksum.getBytes(StandardCharsets.US_ASCII),
                    expectedChecksum.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) throw bad("SHA-256 checksum mismatch");
            return new ValidatedUpload(temporary, filename, expected, size, checksum);
        } catch (ResponseStatusException exception) {
            delete(temporary); throw exception;
        } catch (Exception exception) {
            delete(temporary); throw bad("Unable to validate Course Resource bytes");
        }
    }

    public Path verifiedDownload(InputStream source, long size, String checksum) throws IOException {
        Path temporary = null;
        try (source) {
            temporary = Files.createTempFile(spool, "download-", ".part");
            if (copyBounded(source, temporary, limit) != size || !checksum(temporary).equals(checksum)) throw new IOException("Resource integrity check failed");
            return temporary;
        } catch (Exception exception) {
            if (temporary != null) Files.deleteIfExists(temporary);
            if (exception instanceof IOException io) throw io;
            throw new IOException("Resource integrity check failed");
        }
    }

    public ValidatedUpload validateExisting(Path file, String filename, String contentType) throws IOException {
        return validate(new MultipartFile() {
            public String getName() { return "file"; }
            public String getOriginalFilename() { return filename; }
            public String getContentType() { return contentType; }
            public boolean isEmpty() { return getSize() == 0; }
            public long getSize() { try { return Files.size(file); } catch (IOException e) { throw new java.io.UncheckedIOException(e); } }
            public byte[] getBytes() throws IOException { throw new IOException("Unbounded reads are disabled"); }
            public InputStream getInputStream() throws IOException { return Files.newInputStream(file); }
            public void transferTo(java.io.File destination) throws IOException { throw new IOException("Unbounded copies are disabled"); }
        }, null);
    }

    public void cleanup(Instant cutoff) throws IOException {
        try (var files = Files.list(spool)) {
            for (Path file : files.filter(path -> path.getFileName().toString().matches("(upload|download)-.*\\.part")).toList()) {
                if (Files.getLastModifiedTime(file, java.nio.file.LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff)) Files.deleteIfExists(file);
            }
        }
    }

    public static long copyBounded(InputStream source, Path target, long limit) throws IOException {
        try (source; var out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192]; long size = 0; int count;
            while ((count = source.read(buffer)) != -1) {
                size += count;
                if (size > limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Course Resource exceeds the size limit");
                out.write(buffer, 0, count);
            }
            return size;
        }
    }

    public static String checksum(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void verifyPresentation(Path file) throws IOException {
        boolean types = false, presentation = false; long expanded = 0; int entries = 0;
        try (var zip = new ZipInputStream(Files.newInputStream(file))) {
            java.util.zip.ZipEntry entry; byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (++entries > 1024 || name.startsWith("/") || name.contains("..") || name.contains("\\")
                        || name.toLowerCase(Locale.ROOT).contains("vbaproject")) throw bad("Presentation archive is not supported");
                types |= name.equals("[Content_Types].xml"); presentation |= name.equals("ppt/presentation.xml");
                int count;
                while ((count = zip.read(buffer)) != -1) if ((expanded += count) > 40L * 1024 * 1024) throw bad("Presentation archive is too large");
            }
        }
        if (!types || !presentation) throw bad("File is not a PowerPoint presentation");
    }

    public static String filename(String original) {
        if (original == null) throw bad("Course Resource filename is required");
        String normalized = Normalizer.normalize(original.replace('\\', '/'), Normalizer.Form.NFC);
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        if (normalized.isBlank() || normalized.length() > 180 || normalized.startsWith(".") || normalized.endsWith(".")
                || normalized.indexOf('.') < 1 || normalized.matches(".*[<>:\"|?*].*")) throw bad("Course Resource filename is invalid");
        return normalized;
    }

    private static boolean compatible(String expected, String actual) {
        return expected.equals(actual) || (expected.equals("audio/wav") && actual.equals("audio/vnd.wave"))
                || (expected.equals("audio/wav") && actual.equals("audio/x-wav"))
                || (expected.equals("text/markdown") && actual.equals("text/plain"))
                || (expected.equals("audio/mp4") && actual.equals("video/mp4"))
                || (expected.equals("audio/ogg") && actual.equals("application/ogg"));
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void delete(Path file) { if (file != null) try { Files.deleteIfExists(file); } catch (IOException ignored) { } }
    public record ValidatedUpload(Path path, String fileName, String contentType, long byteSize, String sha256) implements AutoCloseable {
        @Override public void close() throws IOException { Files.deleteIfExists(path); }
    }
}

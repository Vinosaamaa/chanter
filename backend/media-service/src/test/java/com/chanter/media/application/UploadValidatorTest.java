package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class UploadValidatorTest {
    @Test void acceptsWordContainerButRejectsWrongMainPartAndMacros() throws Exception {
        var type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        try (var upload = validator().validate(new MockMultipartFile("file", "notes.docx", type, office("word/document.xml", false)), null)) {
            assertThat(upload.contentType()).isEqualTo(type);
        }
        assertThatThrownBy(() -> validator().validate(new MockMultipartFile("file", "notes.docx", type,
                office("ppt/presentation.xml", false)), null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> validator().validate(new MockMultipartFile("file", "notes.docx", type,
                office("word/document.xml", true)), null)).isInstanceOf(ResponseStatusException.class);
    }
    private static byte[] office(String mainPart, boolean macro) throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bytes)) {
            for (String name : macro ? java.util.List.of("[Content_Types].xml", mainPart, "word/vbaProject.bin")
                    : java.util.List.of("[Content_Types].xml", mainPart)) {
                zip.putNextEntry(new java.util.zip.ZipEntry(name)); zip.write("<fixture/>".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
    @org.junit.jupiter.api.Test
    void failedDownloadSpoolCreationClosesTheProviderStream() throws Exception {
        Path directory = Files.createTempDirectory("media-spool-closed-");
        var validator = new UploadValidator(directory.toString(), 1024);
        Files.delete(directory);
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        var source = new java.io.ByteArrayInputStream(new byte[] {1}) {
            @Override public void close() { closed.set(true); }
        };
        assertThatThrownBy(() -> validator.verifiedDownload(source, 1, "a".repeat(64))).isInstanceOf(java.io.IOException.class);
        assertThat(closed.get()).isTrue();
    }
    private UploadValidator validator() throws Exception {
        return new UploadValidator("target/upload-validator-tests", 1024);
    }

    @Test
    void validatesBytesNormalizesFilenameAndComputesChecksum() throws Exception {
        var bytes = "course notes".getBytes(StandardCharsets.UTF_8);
        try (var upload = validator().validate(new MockMultipartFile("file", "../no\r\ntes.txt", "text/plain", bytes), null)) {
            assertThat(upload.fileName()).isEqualTo("notes.txt");
            assertThat(upload.contentType()).isEqualTo("text/plain");
            assertThat(upload.sha256()).hasSize(64);
            assertThat(Files.readAllBytes(upload.path())).isEqualTo(bytes);
        }
    }

    @Test
    void rejectsDeclaredPdfWhenBytesAreExecutableAndRejectsChecksumMismatch() throws Exception {
        assertThatThrownBy(() -> validator().validate(new MockMultipartFile("file", "notes.pdf", "application/pdf",
                new byte[] {'M', 'Z', 0, 0, 1}), null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> validator().validate(new MockMultipartFile("file", "notes.txt", "text/plain",
                "notes".getBytes(StandardCharsets.UTF_8)), "0".repeat(64))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void enforcesActualStreamLimitEvenWhenMultipartSizeLies() throws Exception {
        var file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1}) {
            @Override public long getSize() { return 1; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[2048]); }
        };
        assertThatThrownBy(() -> validator().validate(file, null)).isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(413));
    }
}

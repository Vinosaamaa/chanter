package com.chanter.agent.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;

/** Bounded, local-only document extraction. Resource content is never executed or fetched. */
public final class ResourceTextExtractor {
    public static final String PARSER_VERSION = "documents-v1";
    public static final int MAX_INPUT_BYTES = 10 * 1024 * 1024;
    public static final int MAX_TEXT_CHARS = 2_000_000;
    private static final int MAX_DOCUMENT_UNITS = 400;
    private static final long MAX_ZIP_BYTES = 64L * 1024 * 1024;
    private static final long MAX_ZIP_ENTRY_BYTES = 16L * 1024 * 1024;
    private ResourceTextExtractor() {}

    public enum Status { READY, EMPTY, OCR_REQUIRED, ENCRYPTED, MALFORMED, UNSUPPORTED, LIMIT_EXCEEDED }
    public record Locator(String kind, int number, String label) {}
    public record Segment(Locator locator, String text) {}
    public record Extraction(Status status, List<Segment> segments, Set<String> signals) {
        public Extraction { segments = List.copyOf(segments); signals = Set.copyOf(signals); }
        public String text() { return String.join("\n\n", segments.stream().map(Segment::text).toList()); }
    }

    public static boolean supportsFileName(String name) {
        return Set.of("txt", "md", "markdown", "pdf", "docx", "pptx").contains(extension(name));
    }

    /** Compatibility for text-only callers; ingestion uses the explicit extraction outcome. */
    public static String extract(byte[] content, String fileName) { return extractDocument(content, fileName).text(); }

    public static Extraction extractDocument(byte[] content, String fileName) {
        if (!supportsFileName(fileName)) return failure(Status.UNSUPPORTED);
        if (content == null || content.length == 0) return failure(Status.EMPTY);
        if (content.length > MAX_INPUT_BYTES) return failure(Status.LIMIT_EXCEEDED);
        var output = new Collector();
        try {
            switch (extension(fileName)) {
                case "txt", "md", "markdown" -> output.add(new Locator("SECTION", 1, "Document"),
                        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString());
                case "pdf" -> readPdf(content, output);
                case "docx" -> { verifyOffice(content, "word/document.xml"); readDocx(content, output); }
                case "pptx" -> { verifyOffice(content, "ppt/presentation.xml"); readPptx(content, output); }
                default -> { return failure(Status.UNSUPPORTED); }
            }
            return new Extraction(output.segments.isEmpty() ? Status.EMPTY : Status.READY, output.segments, output.signals);
        } catch (ExtractionFailure failure) {
            return failure(failure.status);
        } catch (InvalidPasswordException | org.apache.poi.EncryptedDocumentException encrypted) {
            return failure(Status.ENCRYPTED);
        } catch (IOException | RuntimeException malformed) {
            // Parser messages may contain source text, URLs or passwords. Publish only a stable code.
            return failure(Status.MALFORMED);
        }
    }

    private static void readPdf(byte[] content, Collector output) throws IOException {
        try (var document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) throw new ExtractionFailure(Status.ENCRYPTED);
            if (document.getNumberOfPages() > MAX_DOCUMENT_UNITS) throw new ExtractionFailure(Status.LIMIT_EXCEEDED);
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                stripper.setStartPage(i + 1); stripper.setEndPage(i + 1);
                var writer = new BoundedText(MAX_TEXT_CHARS - output.length);
                stripper.writeText(document, writer);
                String text = writer.toString();
                // Text may be drawn as vectors or nested image forms. Never label a graphical page empty.
                if (text.isBlank() && document.getPage(i).hasContents()) throw new ExtractionFailure(Status.OCR_REQUIRED);
                output.add(new Locator("PAGE", i + 1, "Page " + (i + 1)), text);
            }
        }
    }

    private static void readDocx(byte[] content, Collector output) throws IOException {
        try (var document = new XWPFDocument(new ByteArrayInputStream(content))) {
            int section = 1;
            String label = "Document";
            var text = new BoundedText(MAX_TEXT_CHARS);
            for (var element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String style = paragraph.getStyle();
                    if (style != null && style.matches("(?i)heading[1-6]")) {
                        if (!text.toString().isBlank()) {
                            output.add(new Locator("SECTION", section++, label), text.toString());
                            text = new BoundedText(MAX_TEXT_CHARS - output.length);
                        }
                        label = normalize(paragraph.getText(), output.signals);
                        if (label.length() > 200) label = label.substring(0, 200);
                    }
                    text.append(paragraph.getText()).append('\n');
                } else if (element instanceof XWPFTable table) {
                    writeTable(table, text, 0);
                }
            }
            output.add(new Locator("SECTION", section, label), text.toString());
            if (!document.getAllPictures().isEmpty()) {
                if (output.segments.isEmpty()) throw new ExtractionFailure(Status.OCR_REQUIRED);
                output.signals.add("VISUAL_CONTENT_NOT_EXTRACTED");
            }
            // These parts are outside the section-body contract; keep that limitation explicit.
            if (!document.getHeaderList().isEmpty() || !document.getFooterList().isEmpty()) {
                output.signals.add("HEADER_FOOTER_NOT_EXTRACTED");
            }
        }
    }

    private static void writeTable(XWPFTable table, BoundedText text, int depth) throws IOException {
        if (depth > 16) throw new ExtractionFailure(Status.LIMIT_EXCEEDED);
        for (var row : table.getRows()) {
            for (var cell : row.getTableCells()) {
                for (var body : cell.getBodyElements()) {
                    if (body instanceof XWPFParagraph p) text.append(p.getText()).append('\n');
                    else if (body instanceof XWPFTable nested) writeTable(nested, text, depth + 1);
                }
                text.append('\t');
            }
            text.append('\n');
        }
    }

    private static void readPptx(byte[] content, Collector output) throws IOException {
        try (var presentation = new XMLSlideShow(new ByteArrayInputStream(content))) {
            if (presentation.getSlides().size() > MAX_DOCUMENT_UNITS) throw new ExtractionFailure(Status.LIMIT_EXCEEDED);
            int number = 0;
            for (var slide : presentation.getSlides()) {
                var text = new BoundedText(MAX_TEXT_CHARS - output.length);
                writeShapes(slide.getShapes(), text, 0);
                if (text.toString().isBlank() && !slide.getShapes().isEmpty()) throw new ExtractionFailure(Status.OCR_REQUIRED);
                if (slide.getNotes() != null) output.signals.add("SPEAKER_NOTES_NOT_EXTRACTED");
                output.add(new Locator("SLIDE", ++number, "Slide " + number), text.toString());
            }
            if (!presentation.getPictureData().isEmpty()) output.signals.add("VISUAL_CONTENT_NOT_EXTRACTED");
        }
    }

    private static void writeShapes(List<? extends XSLFShape> shapes, BoundedText text, int depth) throws IOException {
        if (depth > 16) throw new ExtractionFailure(Status.LIMIT_EXCEEDED);
        for (var shape : shapes) {
            if (shape instanceof XSLFTextShape paragraph) text.append(paragraph.getText()).append('\n');
            else if (shape instanceof XSLFGroupShape group) writeShapes(group.getShapes(), text, depth + 1);
            else if (shape instanceof XSLFTable table) {
                for (var row : table.getRows()) {
                    for (var cell : row.getCells()) text.append(cell.getText()).append('\t');
                    text.append('\n');
                }
            }
        }
    }

    private static void verifyOffice(byte[] content, String requiredPart) throws IOException {
        if (content.length >= 8 && (content[0] & 0xff) == 0xd0 && (content[1] & 0xff) == 0xcf) {
            try (var ole = new POIFSFileSystem(new ByteArrayInputStream(content))) {
                if (ole.getRoot().hasEntry("EncryptedPackage")) throw new ExtractionFailure(Status.ENCRYPTED);
            }
            throw new ExtractionFailure(Status.MALFORMED);
        }
        if (content.length < 4 || content[0] != 'P' || content[1] != 'K') throw new ExtractionFailure(Status.MALFORMED);
        long total = 0; int entries = 0; boolean mainPart = false, contentTypes = false;
        Set<String> names = new LinkedHashSet<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            var buffer = new byte[8192];
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > 2048) throw new ExtractionFailure(Status.LIMIT_EXCEEDED);
                if (!names.add(entry.getName())) throw new ExtractionFailure(Status.MALFORMED);
                mainPart |= entry.getName().equals(requiredPart);
                contentTypes |= entry.getName().equals("[Content_Types].xml");
                long size = 0;
                for (int read; (read = zip.read(buffer)) != -1;) {
                    total += read; size += read;
                    if (total > MAX_ZIP_BYTES || size > MAX_ZIP_ENTRY_BYTES) throw new ExtractionFailure(Status.LIMIT_EXCEEDED);
                }
            }
        }
        if (!mainPart || !contentTypes) throw new ExtractionFailure(Status.MALFORMED);
    }

    private static String normalize(String text, Set<String> signals) {
        String normalized = Normalizer.normalize(text.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
        if (normalized.codePoints().anyMatch(c -> (c >= 0x202a && c <= 0x202e) || (c >= 0x2066 && c <= 0x2069))) signals.add("DIRECTIONAL_CONTROLS");
        normalized = normalized.replaceAll("[\\u202a-\\u202e\\u2066-\\u2069]", "");
        if (normalized.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t')) throw new ExtractionFailure(Status.MALFORMED);
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("ignore previous instructions") || lower.contains("ignore all previous instructions")
                || lower.contains("<|system|>") || lower.contains("[inst]")) signals.add("INSTRUCTION_MARKERS");
        return normalized.strip();
    }

    private static String extension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
    private static Extraction failure(Status status) { return new Extraction(status, List.of(), Set.of()); }
    private static final class ExtractionFailure extends RuntimeException {
        final Status status;
        ExtractionFailure(Status status) { this.status = status; }
    }
    private static final class Collector {
        final List<Segment> segments = new ArrayList<>();
        final Set<String> signals = new LinkedHashSet<>();
        int length;
        void add(Locator locator, String text) {
            String normalized = normalize(text, signals);
            if (normalized.isBlank()) return;
            length += normalized.length() + (segments.isEmpty() ? 0 : 2);
            if (length > MAX_TEXT_CHARS || segments.size() >= MAX_DOCUMENT_UNITS) throw new ExtractionFailure(Status.LIMIT_EXCEEDED);
            segments.add(new Segment(locator, normalized));
        }
    }
    private static final class BoundedText extends Writer {
        private final StringBuilder text = new StringBuilder();
        private final int limit;
        BoundedText(int limit) { this.limit = limit; }
        @Override public void write(char[] chars, int offset, int length) {
            if (length > limit - text.length()) throw new ExtractionFailure(Status.LIMIT_EXCEEDED);
            text.append(chars, offset, length);
        }
        @Override public void flush() {}
        @Override public void close() {}
        @Override public String toString() { return text.toString(); }
    }
}

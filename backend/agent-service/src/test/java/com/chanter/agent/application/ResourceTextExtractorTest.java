package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResourceTextExtractorTest {

    @Test
    void extractsUtf8ForTxtAndMarkdown() {
        assertThat(ResourceTextExtractor.extract("hello".getBytes(), "notes.txt")).isEqualTo("hello");
        assertThat(ResourceTextExtractor.extract("# Title".getBytes(), "guide.md")).isEqualTo("# Title");
        assertThat(ResourceTextExtractor.extract("x".getBytes(), "a.markdown")).isEqualTo("x");
    }

    @Test
    void skipsUnsupportedTypesAndEmpty() {
        assertThat(ResourceTextExtractor.extract("%PDF-1.4".getBytes(), "slides.pdf")).isEmpty();
        assertThat(ResourceTextExtractor.extract(new byte[0], "notes.txt")).isEmpty();
        assertThat(ResourceTextExtractor.extract(null, "notes.txt")).isEmpty();
        assertThat(ResourceTextExtractor.supportsFileName("deck.pdf")).isFalse();
    }
}

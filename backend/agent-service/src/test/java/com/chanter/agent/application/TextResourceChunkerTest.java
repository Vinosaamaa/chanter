package com.chanter.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextResourceChunkerTest {

    private final TextResourceChunker chunker = new TextResourceChunker(40, 8);

    @Test
    void emptyAndNullReturnNoChunks() {
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("")).isEmpty();
    }

    @Test
    void shortFileIsSingleChunkWithFullOffsets() {
        String text = "short guide";
        List<TextResourceChunker.ChunkSpan> spans = chunker.chunk(text);

        assertThat(spans).hasSize(1);
        assertThat(spans.getFirst().startOffset()).isZero();
        assertThat(spans.getFirst().endOffset()).isEqualTo(text.length());
        assertThat(spans.getFirst().text()).isEqualTo(text);
    }

    @Test
    void unicodeOffsetsMatchSubstring() {
        String text = "café résumé 日本語 " + "word ".repeat(30);
        List<TextResourceChunker.ChunkSpan> spans = new TextResourceChunker(50, 10).chunk(text);

        assertThat(spans).isNotEmpty();
        for (TextResourceChunker.ChunkSpan span : spans) {
            assertThat(text.substring(span.startOffset(), span.endOffset())).isEqualTo(span.text());
        }
        assertThat(spans.getFirst().startOffset()).isZero();
        assertThat(spans.getLast().endOffset()).isEqualTo(text.length());
    }

    @Test
    void longTextProducesOverlappingChunksCoveringFullRange() {
        String text = "alpha beta gamma delta epsilon zeta eta theta iota kappa lambda "
                + "mu nu xi omicron pi rho sigma tau upsilon phi chi psi omega "
                + "and more filler text to force multiple chunks in the unit test.";
        List<TextResourceChunker.ChunkSpan> spans = chunker.chunk(text);

        assertThat(spans.size()).isGreaterThan(1);
        assertThat(spans.getFirst().startOffset()).isZero();
        assertThat(spans.getLast().endOffset()).isEqualTo(text.length());
        for (int i = 1; i < spans.size(); i++) {
            assertThat(spans.get(i).startOffset()).isLessThan(spans.get(i - 1).endOffset());
            assertThat(spans.get(i).startOffset()).isGreaterThanOrEqualTo(spans.get(i - 1).startOffset());
        }
    }
}

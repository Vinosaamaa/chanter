package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

class OnnxEmbeddingClientTest {
    @TempDir Path temporary;

    @Test void corruptOrMissingPinnedAssetsFailClosed() throws Exception {
        Files.writeString(temporary.resolve("model.onnx"), "not model bytes");
        Files.writeString(temporary.resolve("tokenizer.json"), "{}");
        assertThatThrownBy(() -> new OnnxEmbeddingClient(temporary))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("checksum");
        assertThatThrownBy(() -> new OnnxEmbeddingClient(temporary.resolve("missing")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test @EnabledIfEnvironmentVariable(named="CHANTER_TEST_MODEL_DIR", matches=".+")
    void realModelRanksParaphraseAboveUnrelatedTextAndClosesNativeResources() throws Exception {
        try (var client = new OnnxEmbeddingClient(Path.of(System.getenv("CHANTER_TEST_MODEL_DIR")))) {
            var query = client.embed("When is the assignment due?");
            var related = client.embed("Submit your homework before midnight on Friday.");
            var unrelated = client.embed("The mitochondrion supplies energy to cells.");
            assertThat(query).hasSize(384);
            assertThat(dot(query, query)).isCloseTo(1, within(0.0001));
            assertThat(dot(query, related)).isGreaterThan(0.4);
            assertThat(dot(query, related) - dot(query, unrelated)).isGreaterThan(0.3);
            assertThatThrownBy(() -> client.embed(" ")).isInstanceOf(IllegalArgumentException.class);
            assertThat(client.embed("bounded text ".repeat(1000))).hasSize(384);
            assertThatCode(()->VectorValue.encode(client.embed("?! ..."),384)).doesNotThrowAnyException();
        }
    }

    private static double dot(float[] a, float[] b) {
        double result = 0; for (int i=0;i<a.length;i++) result += a[i]*b[i]; return result;
    }
}

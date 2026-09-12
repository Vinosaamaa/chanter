package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;
import com.chanter.media.infra.LocalPrivateResourceStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPrivateResourceStorageTest {
    @TempDir Path directory;
    @Test void immutableCreationIsAtomicAndBytesSurviveAdapterRestart() throws Exception {
        var storage = new LocalPrivateResourceStorage(directory.resolve("private").toString());
        Path source = directory.resolve("source.txt"); Files.writeString(source, "immutable bytes");
        String key = PrivateResourceStorage.PREFIX + UUID.randomUUID() + "/" + UUID.randomUUID() + "/" + UUID.randomUUID();
        var successes = new AtomicInteger();
        var calls = java.util.stream.IntStream.range(0, 8).mapToObj(i -> CompletableFuture.runAsync(() -> {
            try { storage.put(key, source, "unused by local adapter"); successes.incrementAndGet(); }
            catch (java.io.IOException expectedConflict) { assertThat(expectedConflict).isInstanceOf(java.nio.file.FileAlreadyExistsException.class); }
        })).toList(); calls.forEach(CompletableFuture::join);
        assertThat(successes.get()).isEqualTo(1);
        var restarted = new LocalPrivateResourceStorage(directory.resolve("private").toString());
        try (var content = restarted.open(key)) { assertThat(new String(content.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("immutable bytes"); }
        assertThatThrownBy(() -> storage.open("../../source.txt")).isInstanceOf(IllegalArgumentException.class);
        storage.delete(key); storage.delete(key);
        assertThatThrownBy(() -> storage.open(key)).isInstanceOf(java.io.IOException.class);
    }
}

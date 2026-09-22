package com.chanter.media.application;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public interface PrivateResourceStorage {
    String PREFIX = "resources/v1/";
    String backend();
    void put(String key, Path content, String sha256) throws IOException;
    InputStream open(String key) throws IOException;
    void delete(String key) throws IOException;
    Page list(String cursor) throws IOException;
    record ObjectInfo(String key, Instant modifiedAt) { }
    record Page(List<ObjectInfo> objects, String nextCursor) { }
    enum WriteOutcome { NOT_STARTED, FINISHED, UNKNOWN }
    /** Adapter-owned evidence about whether this invocation can still create an object after returning. */
    final class PutFailure extends IOException {
        private final WriteOutcome outcome;
        public PutFailure(WriteOutcome outcome, Throwable cause) {
            super("Private object storage is unavailable",cause);
            this.outcome=java.util.Objects.requireNonNull(outcome);
        }
        public WriteOutcome outcome() { return outcome; }
    }
    /** Adapter-owned evidence about whether this invocation can still remove an object after returning. */
    final class DeleteFailure extends IOException {
        private final WriteOutcome outcome;
        public DeleteFailure(WriteOutcome outcome, Throwable cause) {
            super("Private object storage is unavailable", cause);
            this.outcome = java.util.Objects.requireNonNull(outcome);
        }
        public WriteOutcome outcome() { return outcome; }
    }
    static void requireKey(String key) {
        if (key == null || !key.matches("resources/v1/[a-f0-9-]{36}/[a-f0-9-]{36}/[a-f0-9-]{36}")) {
            throw new IllegalArgumentException("Invalid private resource key");
        }
    }
}

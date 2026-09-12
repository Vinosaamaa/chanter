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
    static void requireKey(String key) {
        if (key == null || !key.matches("resources/v1/[a-f0-9-]{36}/[a-f0-9-]{36}/[a-f0-9-]{36}")) {
            throw new IllegalArgumentException("Invalid private resource key");
        }
    }
}

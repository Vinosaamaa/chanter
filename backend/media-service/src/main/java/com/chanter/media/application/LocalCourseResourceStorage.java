package com.chanter.media.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalCourseResourceStorage {

    private final Path storageRoot;

    public LocalCourseResourceStorage(
            @Value("${chanter.media.storage-dir:./data/course-resources}") String storageDir
    ) throws IOException {
        this.storageRoot = Path.of(storageDir);
        Files.createDirectories(storageRoot);
    }

    public void store(UUID resourceId, byte[] content) throws IOException {
        Files.write(storageRoot.resolve(resourceId.toString()), content);
    }

    public byte[] load(UUID resourceId) throws IOException {
        return Files.readAllBytes(storageRoot.resolve(resourceId.toString()));
    }
}

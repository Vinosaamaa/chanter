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
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(storageRoot);
    }

    public Path legacyPath(UUID resourceId) throws IOException {
        Path file = storageRoot.resolve(resourceId.toString());
        if (Files.isSymbolicLink(storageRoot) || !Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw new IOException("Legacy resource is missing");
        return file;
    }
    public void deleteLegacy(UUID resourceId) throws IOException { Files.deleteIfExists(storageRoot.resolve(resourceId.toString())); }
}

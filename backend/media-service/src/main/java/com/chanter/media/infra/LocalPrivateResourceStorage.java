package com.chanter.media.infra;

import com.chanter.media.application.PrivateResourceStorage;
import com.chanter.media.application.ResourceLifecycle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.stereotype.Component;

@Component
@DependsOnDatabaseInitialization
@ConditionalOnProperty(name = "chanter.media.storage-backend", havingValue = "local")
public class LocalPrivateResourceStorage implements PrivateResourceStorage {
    private final Path root;
    @Autowired
    public LocalPrivateResourceStorage(@Value("${chanter.media.storage-dir}") String root, ResourceLifecycle lifecycle) throws IOException {
        this(root);
        lifecycle.bindNamespace("local\n" + this.root.toRealPath());
    }
    public LocalPrivateResourceStorage(String root) throws IOException {
        this.root = Path.of(root).toAbsolutePath().normalize();
        Files.createDirectories(this.root);
        if (Files.getFileStore(this.root).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(this.root, java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));
    }
    @Override public String backend() { return "local"; }
    private Path path(String key) throws IOException {
        PrivateResourceStorage.requireKey(key);
        Path target = root.resolve(key).normalize();
        for (Path current = target; current != null && current.startsWith(root); current = current.getParent()) {
            if (Files.isSymbolicLink(current)) throw new IOException("Symbolic link in private storage");
        }
        return target;
    }
    @Override public void put(String key, Path content, String checksum) throws IOException {
        Path target = path(key);
        Files.createDirectories(target.getParent());
        // CREATE_NEW atomically rejects existing keys; the DB keeps partial writes quarantined until cleanup.
        try (var out = Files.newOutputStream(target, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)) {
            Files.copy(content, out);
        }
    }
    @Override public InputStream open(String key) throws IOException {
        Path target = path(key);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Private resource is missing");
        return Files.newInputStream(target);
    }
    @Override public void delete(String key) throws IOException { Files.deleteIfExists(path(key)); }
    @Override public Page list(String cursor) throws IOException {
        Path prefix = root.resolve(PREFIX);
        if (!Files.exists(prefix)) return new Page(java.util.List.of(), null);
        try (var files = Files.walk(prefix)) {
            var page = files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .filter(key -> !key.substring(key.lastIndexOf('/') + 1).startsWith("pending-"))
                    .filter(key -> cursor == null || key.compareTo(cursor) > 0).sorted().limit(1001).toList();
            var result = new java.util.ArrayList<ObjectInfo>();
            for (String key : page.stream().limit(1000).toList()) result.add(new ObjectInfo(key, Files.getLastModifiedTime(path(key)).toInstant()));
            return new Page(result, page.size() > 1000 ? page.get(999) : null);
        }
    }
}

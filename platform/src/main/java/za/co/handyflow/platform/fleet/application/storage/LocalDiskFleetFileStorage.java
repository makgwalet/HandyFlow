package za.co.handyflow.platform.fleet.application.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Default, local-disk implementation — fine for development and a single-node
 * deployment, NOT for a multi-instance or ephemeral-container production setup (files
 * would be per-node or lost on redeploy). Replace with an adapter over your shared
 * evidence storage and delete this class; if both exist Spring will report two
 * FleetFileStorage beans.
 * <p>
 * Configure with {@code fleet.documents.storage-path} (default {@code ./data/fleet-documents}).
 */
@Component
public class LocalDiskFleetFileStorage implements FleetFileStorage {

    private final Path root;

    public LocalDiskFleetFileStorage(
            @Value("${fleet.documents.storage-path:./data/fleet-documents}") String storagePath) {
        this.root = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, InputStream content, long sizeBytes, String contentType) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    /** Resolves a key under the root and refuses anything that escapes it. */
    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return p;
    }
}
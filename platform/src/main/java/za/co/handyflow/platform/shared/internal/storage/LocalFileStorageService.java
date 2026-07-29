package za.co.handyflow.platform.shared.internal.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.FileStorageService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Local-disk stand-in for a real object store — see {@link FileStorageService}'s
 * Javadoc for the full rationale. NOT the default: {@code DatabaseFileStorageService}
 * is, since it's more durable for a dev environment (survives redeploys, covered
 * by existing DB backups). This is kept available behind
 * {@code file-storage.provider=local} for local debugging where inspecting a
 * stored file directly on disk is convenient.
 * <p>
 * NOT production-durable: local disk on a container is ephemeral and
 * doesn't replicate. Fine for development; not fine for real user data at
 * rest long-term.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "file-storage.provider", havingValue = "local")
public class LocalFileStorageService implements FileStorageService {

    private final Path basePath;

    public LocalFileStorageService(@Value("${file-storage.local.base-path:./data/file-storage}") String basePathProperty) {
        this.basePath = Paths.get(basePathProperty).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create local file storage directory: " + this.basePath, e);
        }
        log.info("LocalFileStorageService active — storing files under {} (dev stand-in, not production-durable; see FileStorageService Javadoc)", this.basePath);
    }

    @Override
    public String store(String pathPrefix, String originalFilename, String contentType, byte[] content) throws IOException {
        String safeName = sanitize(originalFilename);
        String key = normalizePrefix(pathPrefix) + "/" + UUID.randomUUID() + "-" + safeName;

        Path target = resolveWithinBase(key);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return key;
    }

    @Override
    public byte[] retrieve(String storageKey) throws IOException {
        Path target = resolveWithinBase(storageKey);
        if (!Files.exists(target)) {
            throw new IOException("No file found for storage key: " + storageKey);
        }
        return Files.readAllBytes(target);
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Path target = resolveWithinBase(storageKey);
        Files.deleteIfExists(target); // idempotent, per the interface contract
    }

    /**
     * Resolves a storage key to a path guaranteed to stay inside basePath —
     * rejects any key that would escape it (e.g. via "../"), since storage
     * keys ultimately trace back to user-supplied filenames.
     */
    private Path resolveWithinBase(String storageKey) throws IOException {
        Path resolved = basePath.resolve(storageKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IOException("Rejected storage key outside base path: " + storageKey);
        }
        return resolved;
    }

    private String normalizePrefix(String pathPrefix) {
        return pathPrefix == null ? "misc" : pathPrefix.replaceAll("^/+|/+$", "");
    }

    /** Strips path separators and anything outside a conservative safe set — the actual uniqueness comes from the UUID prefix, not this. */
    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) return "file";
        String base = filename.replaceAll("[\\\\/]", "_");
        base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        return base.length() > 150 ? base.substring(base.length() - 150) : base;
    }
}
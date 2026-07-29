package za.co.handyflow.platform.shared.internal.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.FileStorageService;

import java.io.IOException;
import java.util.UUID;

/**
 * Database-backed stand-in for a real object store — see
 * {@link FileStorageService}'s Javadoc for the full rationale (why this
 * exists, why it's the default over local disk, and why it's still not
 * where file storage should live long-term). Active by default
 * ({@code matchIfMissing = true}) since there's no S3 wired up yet; once
 * there is, flip {@code file-storage.provider} and this stops matching.
 * <p>
 * Storage keys are opaque strings of the form
 * {@code "{pathPrefix}/{uuid}-{sanitizedFilename}"} — the same shape
 * LocalFileStorageService produces, since callers must never depend on a
 * key's internal structure regardless of which implementation is active.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "file-storage.provider", havingValue = "database", matchIfMissing = true)
public class DatabaseFileStorageService implements FileStorageService {

    private final StoredFileBlobRepository repo;

    @Override
    @Transactional
    public String store(String pathPrefix, String originalFilename, String contentType, byte[] content) {
        String key = normalizePrefix(pathPrefix) + "/" + UUID.randomUUID() + "-" + sanitize(originalFilename);
        repo.save(StoredFileBlob.create(key, contentType, content));
        return key;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] retrieve(String storageKey) throws IOException {
        return repo.findById(storageKey)
                .map(StoredFileBlob::getContent)
                .orElseThrow(() -> new IOException("No file found for storage key: " + storageKey));
    }

    @Override
    @Transactional
    public void delete(String storageKey) {
        // existsById-then-delete rather than deleteById directly: deleteById throws
        // if the row is already gone, which would break the interface's idempotency
        // contract ("must not throw if the key is already gone").
        if (repo.existsById(storageKey)) {
            repo.deleteById(storageKey);
        }
    }

    private String normalizePrefix(String pathPrefix) {
        return pathPrefix == null ? "misc" : pathPrefix.replaceAll("^/+|/+$", "");
    }

    /** Same sanitization as LocalFileStorageService — kept identical so key shape doesn't leak which backend is active. */
    private String sanitize(String filename) {
        if (filename == null || filename.isBlank()) return "file";
        String base = filename.replaceAll("[\\\\/]", "_");
        base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        return base.length() > 150 ? base.substring(base.length() - 150) : base;
    }
}
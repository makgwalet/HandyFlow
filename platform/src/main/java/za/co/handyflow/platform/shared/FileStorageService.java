package za.co.handyflow.platform.shared;

import java.io.IOException;

/**
 * Port for binary file storage (task attachments, and any future module that
 * needs to store user-uploaded files). Deliberately provider-agnostic — the
 * rest of the codebase depends only on this interface, never on a specific
 * backend — same shape as {@code SmsSender} in the notifications module.
 * <p>
 * There is currently no S3 (or equivalent object store) wired up for this
 * project — it's still in development. Two stand-ins exist until one is:
 * <ul>
 *   <li>{@code DatabaseFileStorageService} (default) — stores file bytes as
 *       a BYTEA column in the primary database. More durable than local
 *       disk for a dev environment (survives redeploys, rides along with
 *       whatever backup process already covers the rest of the database)
 *       but NOT where this should stay long-term: large binary blobs bloat
 *       the primary DB and every backup/replication of it. Fine for
 *       building and testing the feature now; revisit before real volume.</li>
 *   <li>{@code LocalFileStorageService} — writes to local disk instead.
 *       Kept available behind {@code file-storage.provider=local} for local
 *       debugging (e.g. inspecting a stored file directly on disk), but not
 *       the default — it's strictly worse than the database option for
 *       anything that needs to survive a redeploy.</li>
 * </ul>
 * <p>
 * To go live with a real object store later (S3, Azure Blob, GCS, etc.):
 *   1. Add the provider's SDK dependency.
 *   2. Implement this interface, e.g. {@code S3FileStorageService}.
 *   3. Annotate it {@code @ConditionalOnProperty(name = "file-storage.provider", havingValue = "s3")}.
 *   4. Set {@code file-storage.provider=s3} in application.yml.
 * No caller of this interface needs to change — that's the point of coding
 * to a port here rather than calling a storage SDK directly from TasksService.
 */
public interface FileStorageService {

    /**
     * Stores file bytes under a caller-supplied logical path prefix (e.g.
     * {@code "tasks/" + tenantId + "/" + taskId}) and returns an opaque
     * storage key. Callers must persist that exact key and always pass it
     * back unmodified to {@link #retrieve} / {@link #delete} — never
     * construct, parse, or reconstruct a key themselves, since its format
     * is implementation-specific and will change when a real object-store
     * implementation replaces the local-disk one.
     *
     * @param pathPrefix       logical grouping for the file, e.g. "tasks/{tenantId}/{taskId}"
     * @param originalFilename the uploader's filename, used only for a readable key suffix
     * @param contentType      MIME type as reported by the upload, stored alongside the bytes where the backend supports it
     * @param content          the raw file bytes
     * @return the storage key to persist against the owning entity
     */
    String store(String pathPrefix, String originalFilename, String contentType, byte[] content) throws IOException;

    /** Retrieves previously stored bytes. Throws if the key doesn't exist. */
    byte[] retrieve(String storageKey) throws IOException;

    /** Deletes stored bytes. Must not throw if the key is already gone — deletes are idempotent. */
    void delete(String storageKey) throws IOException;
}
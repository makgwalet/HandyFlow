package za.co.handyflow.platform.documents.application;

import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The one way controlled documents are written and read. It sits on top of the shared {@code FileStorageService} (whichever
 * provider is configured) and adds what that port does not do:
 * <ul>
 *   <li>a registry row per file (tenant, owning module, provider, sizes, the hash of the original and of the exact stored bytes),
 *       so a move to another provider is a copy job and a nightly check can prove every file is intact;</li>
 *   <li>optional AES-256-GCM encryption at rest ({@code app.documents.store.encrypt}), using the platform key that
 *       {@code FieldEncryptionService} already holds, bound to the tenant and the row so a file cannot be served as another's;</li>
 *   <li>a size ceiling ({@code app.documents.store.max-bytes}, 20 MB by default; callers keep their own lower limits);</li>
 *   <li>a hash check on every read: a file that no longer matches is refused with {@link StoredFileIntegrityException} and its
 *       row is marked, so the failure shows on the health check even if nobody retries.</li>
 * </ul>
 * Content checks (allowed types, the malware scan hook) stay with the caller, which knows what it accepts.
 * <p>
 * Storage keys are opaque, exactly as with {@code FileStorageService}: persist the key returned by {@link #put} and pass it back
 * unchanged. Keys written before this store existed have no registry row; they are still read, unencrypted and unchecked, so
 * nothing already stored stops working.
 */
public interface DocumentStore {

    /**
     * Writes a file and records it. {@code ownerModule} is the module code that owns it (e.g. EVIDENCE, DOCUMENTS).
     *
     * @return the storage key to persist with the caller's record
     * @throws IllegalArgumentException for an empty file or one over the ceiling
     * @throws IOException when the storage provider cannot write it
     */
    String put(TenantId tenantId, String ownerModule, String pathPrefix, String fileName, String contentType, byte[] content) throws IOException;

    /**
     * Reads a file back as it was written (decrypted when it was encrypted).
     *
     * @throws IOException when there is no such file for this tenant, it was deleted, or the provider cannot read it
     * @throws StoredFileIntegrityException when the bytes no longer match what was written
     */
    byte[] get(TenantId tenantId, String storageKey) throws IOException;

    /** True when the file is there for this tenant. For a file from before the store existed, the provider is asked. */
    boolean exists(TenantId tenantId, String storageKey);

    /**
     * Deletes the bytes and keeps the registry row as a tombstone with the reason. Deleting something already gone is not an error.
     * Only for files nothing refers to any more (an upload that was refused after it was written, a disposal approved under the
     * retention rules); records that must be kept are archived by their module, never deleted here.
     */
    void delete(TenantId tenantId, String storageKey, String reason) throws IOException;

    /** Platform-wide state of the store, for platform administrators and the health endpoint. */
    Health health();

    /**
     * Checks up to {@code limit} files now (those checked longest ago first), plus a write-read-delete round trip on the provider.
     * Refused with an IllegalStateException when another check is already running.
     */
    RunSummary verifyNow(int limit, UUID triggeredBy);

    record Failing(UUID id, UUID tenantId, String ownerModule, String storageKey, String status, String detail, Instant lastCheckedAt, int failedChecks) {}

    record RunSummary(UUID id, String trigger, Instant startedAt, Instant finishedAt, int checked, int ok, int failed,
                      Boolean canaryOk, String canaryDetail) {}

    /**
     * {@code byStatus}: live files per check status (UNVERIFIED, OK, MISSING, UNREADABLE, MISMATCH).
     * {@code oldestCheckAt}: when the file checked longest ago was last checked (null when nothing has been checked).
     */
    record Health(String provider, boolean encryptionOn, long liveFiles, long encryptedFiles, Map<String, Long> byStatus,
                  Instant oldestCheckAt, RunSummary lastRun, List<Failing> failing) {
        public long failingCount() {
            return byStatus.getOrDefault("MISSING", 0L) + byStatus.getOrDefault("UNREADABLE", 0L) + byStatus.getOrDefault("MISMATCH", 0L);
        }
    }
}

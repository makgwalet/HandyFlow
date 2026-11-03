package za.co.handyflow.platform.documents.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * The record of one file written through the document store: what it must be (hashes, sizes, encryption, provider) and the
 * result of its last check. What the file is never changes (the columns are not updatable and the database refuses a change,
 * trigger in V579); only the check result and the deletion mark do. The row is never deleted.
 */
@Entity
@Table(name = "document_store_objects")
public class StoredObject {

    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false, updatable = false)            private UUID tenantId;
    @Column(name = "owner_module", nullable = false, updatable = false)         private String ownerModule;
    @Column(name = "storage_key", nullable = false, updatable = false)          private String storageKey;
    @Column(name = "storage_provider", nullable = false, updatable = false)     private String storageProvider;
    @Column(name = "content_type", updatable = false)                           private String contentType;
    @Column(name = "size_bytes", nullable = false, updatable = false)           private long sizeBytes;
    @Column(name = "stored_size_bytes", nullable = false, updatable = false)    private long storedSizeBytes;
    @Column(name = "sha256", nullable = false, updatable = false)               private String sha256;
    @Column(name = "stored_sha256", nullable = false, updatable = false)        private String storedSha256;
    @Column(name = "encrypted", nullable = false, updatable = false)            private boolean encrypted;
    @Column(name = "key_id", updatable = false)                                 private String keyId;
    @Column(name = "created_at", nullable = false, updatable = false)           private Instant createdAt;
    @Column(name = "verify_status", nullable = false)                           private String verifyStatus;
    @Column(name = "verify_detail")                                             private String verifyDetail;
    @Column(name = "last_verified_at")                                          private Instant lastVerifiedAt;
    @Column(name = "failed_checks", nullable = false)                           private int failedChecks;
    @Column(name = "deleted_at")                                                private Instant deletedAt;
    @Column(name = "deleted_reason")                                            private String deletedReason;

    protected StoredObject() {}

    /** {@code id} is chosen before the bytes are written, because a sealed file is bound to it. */
    public static StoredObject written(UUID id, UUID tenantId, String ownerModule, String storageKey, String provider, String contentType,
                                       long sizeBytes, String sha256, long storedSizeBytes, String storedSha256, String keyId) {
        StoredObject o = new StoredObject();
        o.id = id;
        o.tenantId = tenantId;
        o.ownerModule = StoreRules.module(ownerModule);
        o.storageKey = storageKey;
        o.storageProvider = StoreRules.clip(provider == null ? "unknown" : provider, 30);
        o.contentType = StoreRules.clip(contentType, 255);
        o.sizeBytes = sizeBytes;
        o.sha256 = sha256;
        o.storedSizeBytes = storedSizeBytes;
        o.storedSha256 = storedSha256;
        o.encrypted = keyId != null;
        o.keyId = keyId;
        o.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        o.verifyStatus = StoreRules.Status.UNVERIFIED.name();
        return o;
    }

    /**
     * Records a check. Returns true when this check turned a good (or never-checked) file bad, which is the moment to raise
     * an alert, so a file that stays bad is reported once rather than every night.
     */
    public boolean checked(StoreRules.Verdict v, Instant at) {
        boolean wasBad = isFailing();
        verifyStatus = v.status().name();
        verifyDetail = StoreRules.clip(v.detail(), 300);
        lastVerifiedAt = at.truncatedTo(ChronoUnit.MICROS);
        failedChecks = v.ok() ? 0 : failedChecks + 1;
        return !v.ok() && !wasBad;
    }

    public void markDeleted(String reason, Instant at) {
        if (deletedAt != null) return;
        deletedAt = at.truncatedTo(ChronoUnit.MICROS);
        deletedReason = StoreRules.clip(reason == null || reason.isBlank() ? "Deleted" : reason, 300);
    }

    public boolean isDeleted() { return deletedAt != null; }
    public boolean isFailing() {
        return !StoreRules.Status.OK.name().equals(verifyStatus) && !StoreRules.Status.UNVERIFIED.name().equals(verifyStatus);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getOwnerModule() { return ownerModule; }
    public String getStorageKey() { return storageKey; }
    public String getStorageProvider() { return storageProvider; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public long getStoredSizeBytes() { return storedSizeBytes; }
    public String getSha256() { return sha256; }
    public String getStoredSha256() { return storedSha256; }
    public boolean isEncrypted() { return encrypted; }
    public String getKeyId() { return keyId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getVerifyStatus() { return verifyStatus; }
    public String getVerifyDetail() { return verifyDetail; }
    public Instant getLastVerifiedAt() { return lastVerifiedAt; }
    public int getFailedChecks() { return failedChecks; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedReason() { return deletedReason; }
}

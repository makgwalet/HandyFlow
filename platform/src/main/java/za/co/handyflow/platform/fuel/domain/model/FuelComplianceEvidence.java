package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A reference to a scanned licence or certificate. Never edited or deleted: a wrong one is replaced with a reason and kept. The file itself is stored elsewhere. */
@Entity
@Table(name = "fuel_compliance_evidence")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelComplianceEvidence {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "item_id", nullable = false, updatable = false) private UUID itemId;
    @Column(name = "file_ref", nullable = false, updatable = false, length = 500) private String fileRef;
    @Column(length = 300, updatable = false) private String note;
    @Column(name = "captured_by", updatable = false) private UUID capturedBy;
    @Column(name = "captured_at", nullable = false, updatable = false) private Instant capturedAt = Instant.now();
    @Column(nullable = false, length = 12) private String status = "ACTIVE";
    @Column(name = "file_stored", nullable = false, updatable = false) private boolean fileStored;
    @Column(name = "content_type", length = 100, updatable = false) private String contentType;
    @Column(name = "file_size_bytes", updatable = false) private Integer fileSizeBytes;
    @Column(name = "file_name", length = 120, updatable = false) private String fileName;
    @Column(name = "verified_by") private UUID verifiedBy;
    @Column(name = "verified_at") private Instant verifiedAt;
    @Column(name = "superseded_reason", length = 300) private String supersededReason;
    @Column(name = "superseded_by") private UUID supersededBy;
    @Column(name = "superseded_at") private Instant supersededAt;
    @Version private Long version;

    public static FuelComplianceEvidence create(TenantId tenantId, UUID itemId, String fileRef, String note, UUID by) {
        if (fileRef == null || fileRef.isBlank()) throw new IllegalArgumentException("Enter the file link or reference.");
        FuelComplianceEvidence e = new FuelComplianceEvidence();
        e.tenantId = tenantId; e.itemId = itemId; e.capturedBy = by;
        e.fileRef = fileRef.trim().length() > 500 ? fileRef.trim().substring(0, 500) : fileRef.trim();
        e.note = note == null || note.isBlank() ? null : (note.trim().length() > 300 ? note.trim().substring(0, 300) : note.trim());
        return e;
    }

    /** A scan or photo kept in the file store; fileRef is the store's key. */
    public static FuelComplianceEvidence createFile(TenantId tenantId, UUID itemId, String storeKey, String contentType, int sizeBytes, String fileName, String note, UUID by) {
        if (storeKey == null || storeKey.isBlank()) throw new IllegalArgumentException("The file was not stored.");
        if (contentType == null || sizeBytes <= 0) throw new IllegalArgumentException("The file is empty.");
        FuelComplianceEvidence e = create(tenantId, itemId, storeKey, note, by);
        e.fileStored = true; e.contentType = contentType; e.fileSizeBytes = sizeBytes;
        e.fileName = fileName == null ? null : (fileName.length() > 120 ? fileName.substring(0, 120) : fileName);
        return e;
    }

    public boolean isVerified() { return verifiedAt != null; }

    /** A second person confirms the document is the real one. Never the person who added it. */
    public void verify(UUID by) {
        if (by == null) throw new IllegalStateException("You are not signed in as a user.");
        if (!isActive()) throw new IllegalStateException("This evidence has been replaced, so it cannot be verified.");
        if (isVerified()) throw new IllegalStateException("This evidence is already verified.");
        if (by.equals(capturedBy)) throw new IllegalStateException("You added this evidence, so someone else must verify it.");
        this.verifiedBy = by; this.verifiedAt = Instant.now();
    }

    public boolean isActive() { return "ACTIVE".equals(status); }

    public void supersede(String reason, UUID by) {
        if (!isActive()) throw new IllegalStateException("This evidence has already been replaced.");
        if (reason == null || reason.trim().length() < 5) throw new IllegalArgumentException("Give a reason of at least 5 characters.");
        this.status = "SUPERSEDED"; this.supersededBy = by; this.supersededAt = Instant.now();
        this.supersededReason = reason.trim().length() > 300 ? reason.trim().substring(0, 300) : reason.trim();
    }
}

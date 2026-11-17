package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.supplychain.domain.enums.SupplierDocType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A supplier compliance document (tax clearance, COID letter, insurance...). The file itself lives in the shared
 * evidence store; this row holds the facts needed to judge compliance. A newer document of the same type
 * SUPERSEDES the older; removal is soft (REMOVED, with a reason). Nothing is ever deleted.
 */
@Entity
@Table(name = "sc_supplier_documents")
@Getter
@NoArgsConstructor
public class ScSupplierDocument {

    public static final String ACTIVE = "ACTIVE";
    public static final String SUPERSEDED = "SUPERSEDED";
    public static final String REMOVED = "REMOVED";

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "supplier_id", nullable = false) UUID supplierId;
    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 40) SupplierDocType docType;
    @Column(name = "reference_number", length = 100) String referenceNumber;
    @Column(name = "issued_date") LocalDate issuedDate;
    @Column(name = "expiry_date") LocalDate expiryDate;
    @Column(nullable = false, length = 12) String status = ACTIVE;
    @Column(nullable = false) boolean verified;
    @Column(name = "verified_by") UUID verifiedBy;
    @Column(name = "verified_by_name", length = 255) String verifiedByName;
    @Column(name = "verified_at") Instant verifiedAt;
    @Column(name = "evidence_id") UUID evidenceId;
    @Column(name = "file_name", length = 255) String fileName;
    @Column(name = "content_type", length = 100) String contentType;
    @Column(name = "uploaded_by") UUID uploadedBy;
    @Column(name = "uploaded_by_name", length = 255) String uploadedByName;
    @Column(name = "removed_reason", length = 500) String removedReason;
    @Column(name = "removed_at") Instant removedAt;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "updated_at", nullable = false) Instant updatedAt;

    public static ScSupplierDocument create(UUID tenantId, UUID supplierId, SupplierDocType type, String referenceNumber,
                                            LocalDate issued, LocalDate expiry, UUID uploadedBy, String uploadedByName) {
        ScSupplierDocument d = new ScSupplierDocument();
        d.id = UUID.randomUUID();
        d.tenantId = tenantId;
        d.supplierId = supplierId;
        d.docType = type;
        d.referenceNumber = referenceNumber != null && referenceNumber.length() > 100 ? referenceNumber.substring(0, 100) : referenceNumber;
        d.issuedDate = issued;
        d.expiryDate = expiry;
        d.uploadedBy = uploadedBy;
        d.uploadedByName = uploadedByName;
        d.createdAt = Instant.now();
        d.updatedAt = d.createdAt;
        return d;
    }

    public boolean isActive() { return ACTIVE.equals(status); }

    public void attachFile(UUID evidenceId, String fileName, String contentType) {
        this.evidenceId = evidenceId;
        this.fileName = fileName != null && fileName.length() > 255 ? fileName.substring(0, 255) : fileName;
        this.contentType = contentType != null && contentType.length() > 100 ? contentType.substring(0, 100) : contentType;
        this.updatedAt = Instant.now();
    }

    /** Replaced by a newer document of the same type. Not an error if it is already not active. */
    public void supersede() {
        if (!isActive()) return;
        this.status = SUPERSEDED;
        this.updatedAt = Instant.now();
    }

    public void markVerified(UUID by, String byName) {
        if (!isActive()) throw new IllegalStateException("Only an ACTIVE document can be verified");
        this.verified = true;
        this.verifiedBy = by;
        this.verifiedByName = byName;
        this.verifiedAt = Instant.now();
        this.updatedAt = this.verifiedAt;
    }

    public void remove(String reason) {
        if (!isActive()) throw new IllegalStateException("Only an ACTIVE document can be removed (this one is " + status + ")");
        this.status = REMOVED;
        this.removedReason = reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason;
        this.removedAt = Instant.now();
        this.updatedAt = this.removedAt;
    }

    /** Whole days until expiry (negative once expired); null when the document never expires. */
    public Long daysToExpiry(LocalDate today) {
        return expiryDate == null ? null : ChronoUnit.DAYS.between(today, expiryDate);
    }
}

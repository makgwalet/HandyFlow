package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One uploaded document (with its file) in a vehicle's vault. File bytes live in
 * the storage backend under {@link #storageKey}; this row is the metadata.
 * Deleting is soft and never removes the stored file — evidence is retained.
 */
@Entity
@Table(name = "fleet_vehicle_documents")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VehicleDocument {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 40)
    private VehicleDocumentType type;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    private String issuer;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Business version within (vehicle, type) — 1, 2, 3... as singleton documents are replaced. */
    @Column(name = "document_version", nullable = false)
    private int documentVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleDocumentStatus status = VehicleDocumentStatus.CURRENT;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public static VehicleDocument create(TenantId tenantId, UUID vehicleId, VehicleDocumentType type,
                                         String documentNumber, LocalDate issueDate, LocalDate expiryDate,
                                         String issuer, String notes, int documentVersion,
                                         String fileName, String contentType, long sizeBytes,
                                         String storageKey, UUID uploadedBy) {
        VehicleDocument d = new VehicleDocument();
        d.tenantId = tenantId;
        d.vehicleId = vehicleId;
        d.type = type;
        d.documentNumber = documentNumber;
        d.issueDate = issueDate;
        d.expiryDate = expiryDate;
        d.issuer = issuer;
        d.notes = notes;
        d.documentVersion = documentVersion;
        d.status = VehicleDocumentStatus.CURRENT;
        d.fileName = fileName;
        d.contentType = contentType;
        d.sizeBytes = sizeBytes;
        d.storageKey = storageKey;
        d.uploadedBy = uploadedBy;
        d.uploadedAt = Instant.now();
        return d;
    }

    public void supersede() {
        this.status = VehicleDocumentStatus.SUPERSEDED;
        this.supersededAt = Instant.now();
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
    }

    public boolean isDeleted() { return deletedAt != null; }
}
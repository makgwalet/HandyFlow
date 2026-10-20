package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** An evidence file attached to one lifecycle event (a sale agreement, a SAPS report, ...). Delete is soft; the file is retained. */
@Entity
@Table(name = "fleet_vehicle_lifecycle_evidence")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class LifecycleEvidence {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 40)
    private LifecycleEvidenceType type;

    @Column(length = 500)
    private String description;

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

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    public static LifecycleEvidence create(TenantId tenantId, UUID vehicleId, UUID eventId,
                                           LifecycleEvidenceType type, String description, String fileName,
                                           String contentType, long sizeBytes, String storageKey, UUID uploadedBy) {
        LifecycleEvidence e = new LifecycleEvidence();
        e.tenantId = tenantId;
        e.vehicleId = vehicleId;
        e.eventId = eventId;
        e.type = type;
        e.description = description;
        e.fileName = fileName;
        e.contentType = contentType;
        e.sizeBytes = sizeBytes;
        e.storageKey = storageKey;
        e.uploadedBy = uploadedBy;
        e.uploadedAt = Instant.now();
        return e;
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
    }
}
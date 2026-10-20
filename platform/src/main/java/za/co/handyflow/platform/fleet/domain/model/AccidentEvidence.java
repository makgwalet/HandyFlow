package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A file kept with an accident case (photos, SAPS report, assessor report, quotes). Delete is soft; the stored file is retained. */
@Entity
@Table(name = "fleet_accident_evidence")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AccidentEvidence {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "accident_id", nullable = false)
    private UUID accidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 30)
    private AccidentEvidenceType type;

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

    public static AccidentEvidence create(TenantId tenantId, UUID accidentId, AccidentEvidenceType type,
                                          String description, String fileName, String contentType,
                                          long sizeBytes, String storageKey, UUID uploadedBy) {
        AccidentEvidence e = new AccidentEvidence();
        e.tenantId = tenantId;
        e.accidentId = accidentId;
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

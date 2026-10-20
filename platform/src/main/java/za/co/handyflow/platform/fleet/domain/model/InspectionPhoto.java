package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A photo attached to an inspection, optionally to one specific checklist item (resultId). */
@Entity
@Table(name = "fleet_inspection_photos")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class InspectionPhoto {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "inspection_id", nullable = false)
    private UUID inspectionId;

    @Column(name = "result_id")
    private UUID resultId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(length = 500)
    private String caption;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    public static InspectionPhoto create(TenantId tenantId, UUID inspectionId, UUID resultId,
                                         String fileName, String contentType, long sizeBytes,
                                         String storageKey, String caption, UUID uploadedBy) {
        InspectionPhoto p = new InspectionPhoto();
        p.tenantId = tenantId;
        p.inspectionId = inspectionId;
        p.resultId = resultId;
        p.fileName = fileName;
        p.contentType = contentType;
        p.sizeBytes = sizeBytes;
        p.storageKey = storageKey;
        p.caption = caption;
        p.uploadedBy = uploadedBy;
        p.uploadedAt = Instant.now();
        return p;
    }
}
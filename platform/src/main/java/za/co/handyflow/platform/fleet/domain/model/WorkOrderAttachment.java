package za.co.handyflow.platform.fleet.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A file on a work order — the quotation, the supplier's invoice, a job card, photos. Soft-deleted; the file is retained. */
@Entity
@Table(name = "fleet_work_order_attachments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WorkOrderAttachment {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", nullable = false, length = 20)
    private WorkOrderAttachmentType type;

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

    public static WorkOrderAttachment create(TenantId tenantId, UUID workOrderId, WorkOrderAttachmentType type,
                                             String description, String fileName, String contentType,
                                             long sizeBytes, String storageKey, UUID uploadedBy) {
        WorkOrderAttachment a = new WorkOrderAttachment();
        a.tenantId = tenantId;
        a.workOrderId = workOrderId;
        a.type = type;
        a.description = description;
        a.fileName = fileName;
        a.contentType = contentType;
        a.sizeBytes = sizeBytes;
        a.storageKey = storageKey;
        a.uploadedBy = uploadedBy;
        a.uploadedAt = Instant.now();
        return a;
    }

    public void softDelete(UUID deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
    }
}
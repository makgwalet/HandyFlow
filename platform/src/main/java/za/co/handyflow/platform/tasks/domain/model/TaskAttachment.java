package za.co.handyflow.platform.tasks.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

// ── TaskAttachment ────────────────────────────────────────────────────────────
@Entity
@Table(name = "task_attachments")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TaskAttachment {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "task_id",          nullable = false) private UUID    taskId;
    @Column(name = "tenant_id",        nullable = false) private UUID    tenantId;
    @Column(name = "file_name",        nullable = false) private String  fileName;
    @Column(name = "content_type")                        private String  contentType;
    @Column(name = "size_bytes",       nullable = false) private long    sizeBytes;
    /** Opaque key returned by FileStorageService.store() — never construct or parse this. */
    @Column(name = "storage_key",      nullable = false) private String  storageKey;
    @Column(name = "uploaded_by")                         private UUID    uploadedBy;
    @Column(name = "uploaded_by_name", nullable = false) private String  uploadedByName;
    @Column(name = "created_at")                          private Instant createdAt;

    public static TaskAttachment create(UUID taskId, UUID tenantId, String fileName, String contentType,
                                        long sizeBytes, String storageKey,
                                        UUID uploadedBy, String uploadedByName) {
        TaskAttachment a   = new TaskAttachment();
        a.taskId           = taskId;
        a.tenantId         = tenantId;
        a.fileName         = fileName;
        a.contentType      = contentType;
        a.sizeBytes        = sizeBytes;
        a.storageKey       = storageKey;
        a.uploadedBy       = uploadedBy;
        a.uploadedByName   = uploadedByName;
        a.createdAt        = Instant.now();
        return a;
    }
}
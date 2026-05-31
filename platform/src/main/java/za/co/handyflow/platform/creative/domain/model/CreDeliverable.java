package za.co.handyflow.platform.creative.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cre_deliverables")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CreDeliverable {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "job_id",    nullable = false) private UUID   jobId;
    @Column(name = "tenant_id", nullable = false) private UUID   tenantId;
    @Column(name = "file_url",  nullable = false) private String fileUrl;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "file_type") private String  fileType;
    @Column(name = "file_size") private Long    fileSize;
    private String notes;
    @Column(name = "uploaded_by") private UUID    uploadedBy;
    @Column(name = "created_at")  private Instant createdAt;

    public static CreDeliverable create(UUID jobId, UUID tenantId,
                                         String fileUrl, String fileName,
                                         String fileType, Long fileSize,
                                         String notes, UUID uploadedBy) {
        CreDeliverable d = new CreDeliverable();
        d.jobId      = jobId;
        d.tenantId   = tenantId;
        d.fileUrl    = fileUrl;
        d.fileName   = fileName;
        d.fileType   = fileType;
        d.fileSize   = fileSize;
        d.notes      = notes;
        d.uploadedBy = uploadedBy;
        d.createdAt  = Instant.now();
        return d;
    }
}

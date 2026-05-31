package za.co.handyflow.platform.creative.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cre_proofs")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CreProof {

    @Id private UUID id = UUID.randomUUID();

    @Column(name = "job_id",    nullable = false) private UUID   jobId;
    @Column(name = "tenant_id", nullable = false) private UUID   tenantId;
    @Column(name = "version_number", nullable = false) private int versionNumber = 1;

    private String title;
    @Column(name = "file_url")      private String fileUrl;
    @Column(name = "file_name")     private String fileName;
    @Column(name = "file_type")     private String fileType;
    @Column(name = "thumbnail_url") private String thumbnailUrl;

    @Column(nullable = false) private String status = "PENDING";

    @Column(name = "approval_token",   nullable = false, unique = true) private String  approvalToken;
    @Column(name = "token_expires_at", nullable = false)                private Instant tokenExpiresAt;

    @Column(name = "sent_at")           private Instant sentAt;
    @Column(name = "sent_to_email")     private String  sentToEmail;
    @Column(name = "approved_at")       private Instant approvedAt;
    @Column(name = "approved_by_name")  private String  approvedByName;
    @Column(name = "approved_by_email") private String  approvedByEmail;
    @Column(name = "approved_by_ip")    private String  approvedByIp;
    @Column(name = "rejection_reason")  private String  rejectionReason;

    private String notes;
    @Column(name = "uploaded_by") private UUID    uploadedBy;
    @Column(name = "created_at")  private Instant createdAt;

    public static CreProof create(UUID jobId, UUID tenantId, int versionNumber,
                                   String title, String fileUrl, String fileName,
                                   String fileType, String thumbnailUrl,
                                   String notes, UUID uploadedBy) {
        CreProof p       = new CreProof();
        p.id             = UUID.randomUUID();
        p.jobId          = jobId;
        p.tenantId       = tenantId;
        p.versionNumber  = versionNumber;
        p.title          = title;
        p.fileUrl        = fileUrl;
        p.fileName       = fileName;
        p.fileType       = fileType;
        p.thumbnailUrl   = thumbnailUrl;
        p.notes          = notes;
        p.uploadedBy     = uploadedBy;
        p.status         = "PENDING";
        // Generate secure 64-char token
        p.approvalToken  = UUID.randomUUID().toString().replace("-","")
                         + UUID.randomUUID().toString().replace("-","");
        p.tokenExpiresAt = Instant.now().plusSeconds(72 * 3600); // 72 hours
        p.createdAt      = Instant.now();
        return p;
    }

    // ── Client approval actions ───────────────────────────────────────────────

    public void markSent(String email) {
        this.sentAt       = Instant.now();
        this.sentToEmail  = email;
    }

    public void approve(String clientName, String clientEmail, String clientIp) {
        this.status           = "APPROVED";
        this.approvedAt       = Instant.now();
        this.approvedByName   = clientName;
        this.approvedByEmail  = clientEmail;
        this.approvedByIp     = clientIp;
    }

    public void reject(String reason) {
        this.status          = "REJECTED";
        this.rejectionReason = reason;
    }

    public void supersede() {
        this.status = "SUPERSEDED";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isTokenValid() {
        return "PENDING".equals(status)
                && Instant.now().isBefore(tokenExpiresAt);
    }

    public boolean isPending()   { return "PENDING".equals(status); }
    public boolean isApproved()  { return "APPROVED".equals(status); }
    public boolean isRejected()  { return "REJECTED".equals(status); }
}

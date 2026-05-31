package za.co.handyflow.platform.recruiter.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rec_applications")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RecApplication {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "job_id",       nullable = false) private UUID   jobId;
    @Column(name = "applicant_id", nullable = false) private UUID   applicantId;
    @Column(nullable = false)                         private String stage  = "APPLIED";
    private String  source = "CAREERS_PAGE";
    private Integer score;
    private String  notes;
    @Column(name = "rejection_reason")  private String  rejectionReason;
    @Column(name = "hr_employee_id")    private UUID    hrEmployeeId;
    @Column(name = "applied_at")        private Instant appliedAt;
    @Column(name = "stage_changed_at")  private Instant stageChangedAt;
    @Column(name = "hired_at")          private Instant hiredAt;
    @Column(name = "created_at")        private Instant createdAt;
    @Column(name = "updated_at")        private Instant updatedAt;

    public static RecApplication create(TenantId tenantId, UUID jobId,
                                         UUID applicantId, String source) {
        RecApplication a  = new RecApplication();
        a.tenantId        = tenantId;
        a.jobId           = jobId;
        a.applicantId     = applicantId;
        a.source          = source != null ? source : "CAREERS_PAGE";
        a.stage           = "APPLIED";
        a.appliedAt       = Instant.now();
        a.stageChangedAt  = Instant.now();
        a.createdAt       = Instant.now();
        a.updatedAt       = Instant.now();
        return a;
    }

    // ── Pipeline progression ──────────────────────────────────────────────────

    public void moveToStage(String newStage) {
        this.stage          = newStage;
        this.stageChangedAt = Instant.now();
        if ("HIRED".equals(newStage)) this.hiredAt = Instant.now();
        touch();
    }

    public void reject(String reason) {
        this.stage           = "REJECTED";
        this.rejectionReason = reason;
        this.stageChangedAt  = Instant.now();
        touch();
    }

    public void withdraw() {
        this.stage          = "WITHDRAWN";
        this.stageChangedAt = Instant.now();
        touch();
    }

    public void linkToEmployee(UUID hrEmployeeId) {
        this.hrEmployeeId = hrEmployeeId;
        touch();
    }

    public void updateNotes(String notes) { this.notes = notes; touch(); }
    public void updateScore(int score)    { this.score = score; touch(); }

    private void touch() { this.updatedAt = Instant.now(); }

    public boolean isActive() {
        return !"REJECTED".equals(stage) && !"WITHDRAWN".equals(stage) && !"HIRED".equals(stage);
    }
}

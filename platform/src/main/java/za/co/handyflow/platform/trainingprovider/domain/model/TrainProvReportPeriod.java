package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.ReportRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One reporting window under an obligation. A submission is a staff record, an acknowledgement is recorded separately. Never deleted. */
@Entity(name = "TrainProvReportPeriod")
@Table(name = "trainprov_report_periods")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvReportPeriod {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "period_no", nullable = false)
    private String periodNo;
    @Column(name = "obligation_id", nullable = false, updatable = false)
    private UUID obligationId;
    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;
    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;
    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;
    @Column(name = "due_override_reason")
    private String dueOverrideReason;
    @Column(nullable = false)
    private String status = ReportRules.OPEN;
    @Column(name = "submitted_on")
    private LocalDate submittedOn;
    @Column(name = "submitted_by")
    private UUID submittedBy;
    @Column(name = "submission_ref")
    private String submissionRef;
    @Column(name = "acknowledged_on")
    private LocalDate acknowledgedOn;
    @Column(name = "acknowledgement_ref")
    private String acknowledgementRef;
    @Column(name = "waived_reason")
    private String waivedReason;
    @Column(name = "waived_by")
    private UUID waivedBy;
    @Column(name = "void_reason")
    private String voidReason;
    @Column(name = "void_by")
    private UUID voidBy;
    private String note;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvReportPeriod create(TenantId t, String no, UUID obligationId, LocalDate start, LocalDate end, LocalDate due, String overrideReason, UUID by) {
        TrainProvReportPeriod p = new TrainProvReportPeriod();
        p.tenantId = t.getValue();
        p.periodNo = no;
        p.obligationId = obligationId;
        p.periodStart = start;
        p.periodEnd = end;
        p.dueOn = due;
        p.dueOverrideReason = overrideReason == null || overrideReason.isBlank() ? null : overrideReason.trim();
        p.createdBy = by;
        p.createdAt = Instant.now();
        p.updatedAt = p.createdAt;
        return p;
    }

    public void submit(LocalDate on, String ref, String note, UUID by, LocalDate today) {
        String problem = ReportRules.submitProblem(status, on, periodStart, today);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = ReportRules.SUBMITTED;
        this.submittedOn = on;
        this.submittedBy = by;
        this.submissionRef = blank(ref);
        this.note = blank(note);
        this.updatedAt = Instant.now();
    }

    public void acknowledge(LocalDate on, String ref, LocalDate today) {
        String problem = ReportRules.acknowledgeProblem(status, acknowledgedOn != null, on, submittedOn, today);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.acknowledgedOn = on;
        this.acknowledgementRef = blank(ref);
        this.updatedAt = Instant.now();
    }

    public void waive(String reason, UUID by) {
        String problem = ReportRules.waiveProblem(status, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = ReportRules.WAIVED;
        this.waivedReason = reason.trim();
        this.waivedBy = by;
        this.updatedAt = Instant.now();
    }

    public void voidIt(String reason, UUID by) {
        String problem = ReportRules.voidProblem(status, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = ReportRules.VOID;
        this.voidReason = reason.trim();
        this.voidBy = by;
        this.updatedAt = Instant.now();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

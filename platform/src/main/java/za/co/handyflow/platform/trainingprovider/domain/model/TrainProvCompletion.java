package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.CompletionRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A person's recorded outcome for an enrolment. Not a certificate. Withdrawn with a reason, never deleted. */
@Entity(name = "TrainProvCompletion")
@Table(name = "trainprov_completions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvCompletion {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "completion_no", nullable = false)
    private String completionNo;
    @Column(name = "enrolment_id", nullable = false)
    private UUID enrolmentId;
    @Column(nullable = false)
    private String outcome;
    @Column(name = "outcome_on", nullable = false)
    private LocalDate outcomeOn;
    @Column(nullable = false)
    private String basis;
    @Column(name = "open_gaps", nullable = false)
    private int openGaps;
    @Column(name = "gaps_snapshot")
    private String gapsSnapshot;
    @Column(name = "gaps_override_reason")
    private String gapsOverrideReason;
    @Column(nullable = false)
    private String status = CompletionRules.RECORDED;
    @Column(name = "withdrawn_reason")
    private String withdrawnReason;
    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;
    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;
    @Column(name = "recorded_by", nullable = false)
    private UUID recordedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvCompletion create(TenantId t, String no, UUID enrolmentId, String outcome, LocalDate on, String basis, int openGaps, String gapsSnapshot,
                                             String overrideReason, UUID by) {
        TrainProvCompletion c = new TrainProvCompletion();
        c.tenantId = t.getValue();
        c.completionNo = no;
        c.enrolmentId = enrolmentId;
        c.outcome = outcome;
        c.outcomeOn = on;
        c.basis = basis.trim();
        c.openGaps = openGaps;
        c.gapsSnapshot = gapsSnapshot == null || gapsSnapshot.isBlank() ? null : gapsSnapshot;
        c.gapsOverrideReason = overrideReason == null || overrideReason.isBlank() ? null : overrideReason.trim();
        c.recordedBy = by;
        c.createdAt = Instant.now();
        c.updatedAt = c.createdAt;
        return c;
    }

    public void withdraw(String reason, UUID by) {
        String problem = CompletionRules.withdrawProblem(status, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = CompletionRules.WITHDRAWN;
        this.withdrawnReason = reason.trim();
        this.withdrawnBy = by;
        this.withdrawnAt = Instant.now();
        this.updatedAt = this.withdrawnAt;
    }
}

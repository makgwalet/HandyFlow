package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** The competency decision on one attempt. Written once and never changed. */
@Entity(name = "TrainProvAssessmentDecision")
@Table(name = "trainprov_assessment_decisions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAssessmentDecision {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;
    @Column(nullable = false, updatable = false)
    private String decision;
    @Column(nullable = false, updatable = false)
    private String basis;
    @Column(name = "decided_by", nullable = false, updatable = false)
    private UUID decidedBy;
    @Column(name = "decided_on", nullable = false, updatable = false)
    private LocalDate decidedOn;
    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;
    /** Set when the assessor has no login and the decision was recorded for them: how it reached the person who recorded it. */
    @Column(name = "recorded_for_assessor", updatable = false)
    private String recordedForAssessor;

    public static TrainProvAssessmentDecision of(TenantId t, UUID attemptId, String decision, String basis, UUID by, LocalDate today) {
        return of(t, attemptId, decision, basis, by, today, null);
    }

    public static TrainProvAssessmentDecision of(TenantId t, UUID attemptId, String decision, String basis, UUID by, LocalDate today, String recordedForAssessor) {
        TrainProvAssessmentDecision d = new TrainProvAssessmentDecision();
        d.tenantId = t.getValue();
        d.attemptId = attemptId;
        d.decision = decision;
        d.basis = basis.trim();
        d.decidedBy = by;
        d.decidedOn = today;
        d.decidedAt = Instant.now();
        d.recordedForAssessor = recordedForAssessor == null || recordedForAssessor.isBlank() ? null : recordedForAssessor.trim();
        return d;
    }
}

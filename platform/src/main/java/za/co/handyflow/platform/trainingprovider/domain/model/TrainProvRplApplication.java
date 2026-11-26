package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.CohortRules;
import za.co.handyflow.platform.trainingprovider.domain.rules.RplRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An RPL application. Decided with a written basis, confirmed by a second person (or alone with a reason), withdrawn with a reason, never deleted. */
@Entity(name = "TrainProvRplApplication")
@Table(name = "trainprov_rpl_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvRplApplication {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "rpl_no", nullable = false)
    private String rplNo;
    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private UUID enrolmentId;
    @Column(name = "module_id", updatable = false)
    private UUID moduleId;
    @Column(nullable = false)
    private String claim;
    @Column(name = "applied_on", nullable = false)
    private LocalDate appliedOn;
    @Column(nullable = false)
    private String status = RplRules.OPEN;
    private String outcome;
    @Column(name = "decision_basis")
    private String decisionBasis;
    @Column(name = "assessor_name")
    private String assessorName;
    @Column(name = "assessor_id")
    private UUID assessorId;
    @Column(name = "assessor_appointment_id")
    private UUID assessorAppointmentId;
    /** How the decision reached the person recording it, when the assessor has no login. */
    @Column(name = "recorded_for_assessor")
    private String recordedForAssessor;
    /** True for a decision made before assessors were checked: only a typed name exists. */
    @Column(name = "legacy_assessor_text", nullable = false)
    private boolean legacyAssessorText;
    @Column(name = "decided_on")
    private LocalDate decidedOn;
    @Column(name = "decided_by")
    private UUID decidedBy;
    @Column(name = "single_reason")
    private String singleReason;
    @Column(name = "confirmed_on")
    private LocalDate confirmedOn;
    @Column(name = "confirmed_by")
    private UUID confirmedBy;
    @Column(name = "withdraw_reason")
    private String withdrawReason;
    @Column(name = "withdrawn_by")
    private UUID withdrawnBy;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvRplApplication open(TenantId t, String no, UUID enrolmentId, UUID moduleId, String claim, LocalDate appliedOn, UUID by) {
        TrainProvRplApplication a = new TrainProvRplApplication();
        a.tenantId = t.getValue();
        a.rplNo = no;
        a.enrolmentId = enrolmentId;
        a.moduleId = moduleId;
        a.claim = claim.trim();
        a.appliedOn = appliedOn;
        a.createdBy = by;
        a.createdAt = Instant.now();
        a.updatedAt = a.createdAt;
        return a;
    }

    public boolean confirmedAlone() {
        return singleReason != null;
    }

    /** Records the decision. With confirmAlone the decider's written reason stands in for a second person. */
    public void decide(String outcome, String basis, String assessor, UUID assessorId, UUID assessorAppointmentId, String recordedForAssessor, LocalDate on, UUID by,
                       boolean confirmAlone, String reason) {
        if (!RplRules.OPEN.equals(status)) {
            throw new IllegalStateException("Only an open application can be decided");
        }
        if (confirmAlone && !CohortRules.longEnough(reason, 10)) {
            throw new IllegalStateException("To confirm without a second person, say why in at least 10 characters");
        }
        this.outcome = outcome;
        this.decisionBasis = basis.trim();
        this.assessorName = assessor.trim();
        this.assessorId = assessorId;
        this.assessorAppointmentId = assessorAppointmentId;
        this.recordedForAssessor = recordedForAssessor == null || recordedForAssessor.isBlank() ? null : recordedForAssessor.trim();
        this.legacyAssessorText = false;
        this.decidedOn = on;
        this.decidedBy = by;
        if (confirmAlone) {
            this.singleReason = reason.trim();
            this.status = RplRules.CONFIRMED;
        } else {
            this.status = RplRules.DECIDED;
        }
        this.updatedAt = Instant.now();
    }

    public void confirm(UUID by, LocalDate on, LocalDate today) {
        String problem = RplRules.confirmProblem(status, decidedBy, by, on, decidedOn, today);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.confirmedBy = by;
        this.confirmedOn = on;
        this.status = RplRules.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void withdraw(String reason, UUID by) {
        String problem = RplRules.withdrawProblem(status, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = RplRules.WITHDRAWN;
        this.withdrawReason = reason.trim();
        this.withdrawnBy = by;
        this.updatedAt = Instant.now();
    }
}

package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.AttemptRules;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A learner's attempt at an assessment instrument. The mark lives here; the competency decision is a separate record. Never deleted: voided with a reason. */
@Entity(name = "TrainProvAssessmentAttempt")
@Table(name = "trainprov_assessment_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAssessmentAttempt {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "attempt_no", nullable = false)
    private String attemptNo;
    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;
    @Column(name = "member_id", nullable = false)
    private UUID memberId;
    @Column(name = "cohort_id", nullable = false)
    private UUID cohortId;
    @Column(name = "enrolment_id", nullable = false)
    private UUID enrolmentId;
    @Column(name = "attempt_seq", nullable = false)
    private int attemptSeq;
    @Column(name = "assessed_on", nullable = false)
    private LocalDate assessedOn;
    @Column(name = "assessor_id", nullable = false)
    private UUID assessorId;
    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;
    private BigDecimal mark;
    private String notes;
    @Column(nullable = false)
    private String status = AttemptRules.RECORDED;
    @Column(name = "void_reason")
    private String voidReason;
    @Column(name = "void_by")
    private UUID voidBy;
    @Column(name = "void_at")
    private Instant voidAt;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvAssessmentAttempt create(TenantId t, String no, UUID instrumentId, UUID memberId, UUID cohortId, UUID enrolmentId, int seq,
                                                    LocalDate assessedOn, UUID assessorId, UUID appointmentId, BigDecimal mark, String notes, UUID by) {
        TrainProvAssessmentAttempt a = new TrainProvAssessmentAttempt();
        a.tenantId = t.getValue();
        a.attemptNo = no;
        a.instrumentId = instrumentId;
        a.memberId = memberId;
        a.cohortId = cohortId;
        a.enrolmentId = enrolmentId;
        a.attemptSeq = seq;
        a.assessedOn = assessedOn;
        a.assessorId = assessorId;
        a.appointmentId = appointmentId;
        a.mark = mark;
        a.notes = notes == null || notes.isBlank() ? null : notes.trim();
        a.createdBy = by;
        a.createdAt = Instant.now();
        a.updatedAt = a.createdAt;
        return a;
    }

    public void correctMark(BigDecimal maxMark, BigDecimal newMark, boolean decided, String reason) {
        String problem = AttemptRules.correctionProblem(status, decided, maxMark, newMark, mark, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.mark = newMark;
        this.updatedAt = Instant.now();
    }

    public void voidIt(String reason, UUID by) {
        String problem = AttemptRules.voidProblem(status, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = AttemptRules.VOID;
        this.voidReason = reason.trim();
        this.voidBy = by;
        this.voidAt = Instant.now();
        this.updatedAt = this.voidAt;
    }
}

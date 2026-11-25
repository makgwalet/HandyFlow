package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.EnrolmentRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A learner's enrolment on a programme, frozen to the programme version and site they started on.
 * The cover outcome is what the accreditation check said on the day; it is never recalculated.
 */
@Entity(name = "TrainProvLearnerEnrolment")
@Table(name = "trainprov_learner_enrolments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvLearnerEnrolment {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "enrolment_no", nullable = false)
    private String enrolmentNo;
    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;
    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;
    @Column(name = "programme_version_id", nullable = false)
    private UUID programmeVersionId;
    @Column(name = "qualification_version_id")
    private UUID qualificationVersionId;
    @Column(name = "site_id", nullable = false)
    private UUID siteId;
    @Column(name = "application_id")
    private UUID applicationId;
    @Column(name = "client_id")
    private UUID clientId;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "expected_end_date")
    private LocalDate expectedEndDate;
    @Column(nullable = false)
    private String status = EnrolmentRules.ENROLLED;
    @Column(name = "status_reason")
    private String statusReason;
    @Column(name = "training_activity", nullable = false)
    private String trainingActivity;
    @Column(name = "cover_outcome")
    private String coverOutcome;
    @Column(name = "cover_checked_on")
    private LocalDate coverCheckedOn;
    @Column(name = "cover_override_reason")
    private String coverOverrideReason;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvLearnerEnrolment create(TenantId t, String enrolmentNo, UUID learnerId, UUID programmeId, UUID programmeVersionId,
                                                   UUID qualificationVersionId, UUID siteId, UUID applicationId, UUID clientId,
                                                   LocalDate start, LocalDate expectedEnd, String activity, String coverOutcome,
                                                   LocalDate coverCheckedOn, String coverOverrideReason, UUID by) {
        TrainProvLearnerEnrolment e = new TrainProvLearnerEnrolment();
        e.tenantId = t.getValue();
        e.enrolmentNo = enrolmentNo;
        e.learnerId = learnerId;
        e.programmeId = programmeId;
        e.programmeVersionId = programmeVersionId;
        e.qualificationVersionId = qualificationVersionId;
        e.siteId = siteId;
        e.applicationId = applicationId;
        e.clientId = clientId;
        e.startDate = start;
        e.expectedEndDate = expectedEnd;
        e.trainingActivity = activity;
        if ("ACCREDITED".equals(activity)) {
            e.coverOutcome = coverOutcome;
            e.coverCheckedOn = coverCheckedOn;
            boolean covered = EnrolmentRules.isCovered(coverOutcome);
            e.coverOverrideReason = covered || coverOverrideReason == null || coverOverrideReason.isBlank() ? null : coverOverrideReason.trim();
        }
        e.createdBy = by;
        e.createdAt = Instant.now();
        e.updatedAt = e.createdAt;
        return e;
    }

    public void moveTo(String to, String reason) {
        String problem = EnrolmentRules.enrolmentTransitionProblem(status, to, reason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.status = to;
        this.statusReason = reason == null || reason.isBlank() ? null : reason.trim();
        this.updatedAt = Instant.now();
    }
}

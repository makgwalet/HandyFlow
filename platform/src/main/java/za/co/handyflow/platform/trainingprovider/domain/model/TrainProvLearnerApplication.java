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

/** A learner's application to a programme. A person records the entry check; the system never decides eligibility. */
@Entity(name = "TrainProvLearnerApplication")
@Table(name = "trainprov_learner_applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvLearnerApplication {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;
    @Column(name = "programme_id", nullable = false)
    private UUID programmeId;
    @Column(name = "site_id")
    private UUID siteId;
    @Column(name = "preferred_start")
    private LocalDate preferredStart;
    @Column(name = "source_note")
    private String sourceNote;
    @Column(nullable = false)
    private String status = EnrolmentRules.RECEIVED;
    @Column(name = "status_reason")
    private String statusReason;
    @Column(name = "entry_requirements_met")
    private Boolean entryRequirementsMet;
    @Column(name = "entry_check_note")
    private String entryCheckNote;
    @Column(name = "entry_checked_by")
    private UUID entryCheckedBy;
    @Column(name = "entry_checked_at")
    private Instant entryCheckedAt;
    @Column(name = "entry_override_reason")
    private String entryOverrideReason;
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    public static TrainProvLearnerApplication create(TenantId t, UUID learnerId, UUID programmeId, UUID siteId, LocalDate preferredStart,
                                                     String sourceNote, UUID by) {
        TrainProvLearnerApplication a = new TrainProvLearnerApplication();
        a.tenantId = t.getValue();
        a.learnerId = learnerId;
        a.programmeId = programmeId;
        a.siteId = siteId;
        a.preferredStart = preferredStart;
        a.sourceNote = sourceNote == null || sourceNote.isBlank() ? null : sourceNote.trim();
        a.createdBy = by;
        a.createdAt = Instant.now();
        a.updatedAt = a.createdAt;
        return a;
    }

    /** Moves the application on. The check is repeated here so no caller can skip it. */
    public void moveTo(String to, String reason, Boolean entryMet, String entryNote, String overrideReason, UUID by) {
        String problem = EnrolmentRules.applicationProblem(status, to, reason, entryMet, entryNote, overrideReason);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        if (EnrolmentRules.ACCEPTED.equals(to)) {
            this.entryRequirementsMet = entryMet;
            this.entryCheckNote = entryNote.trim();
            this.entryCheckedBy = by;
            this.entryCheckedAt = Instant.now();
            this.entryOverrideReason = entryMet ? null : overrideReason.trim();
        }
        this.statusReason = reason == null || reason.isBlank() ? null : reason.trim();
        this.status = to;
        this.updatedAt = Instant.now();
    }

    /** Called by enrolling. The application must have been accepted. */
    public void markEnrolled() {
        if (!EnrolmentRules.ACCEPTED.equals(status)) {
            throw new IllegalStateException("Only an accepted application can be enrolled");
        }
        this.status = EnrolmentRules.ENROLLED;
        this.updatedAt = Instant.now();
    }
}

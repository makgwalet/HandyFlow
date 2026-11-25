package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.rules.ModerationRules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Moderation of one decided attempt. Written once; only the resolution of a query is added later, once. Never deleted. */
@Entity(name = "TrainProvAssessmentModeration")
@Table(name = "trainprov_assessment_moderations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAssessmentModeration {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "moderation_no", nullable = false, updatable = false)
    private String moderationNo;
    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;
    @Column(name = "moderator_id", nullable = false, updatable = false)
    private UUID moderatorId;
    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;
    @Column(name = "moderated_on", nullable = false, updatable = false)
    private LocalDate moderatedOn;
    @Column(nullable = false, updatable = false)
    private String outcome;
    @Column(nullable = false, updatable = false)
    private String basis;
    @Column(name = "entered_by", nullable = false, updatable = false)
    private UUID enteredBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "resolution_note")
    private String resolutionNote;
    @Column(name = "resolved_by")
    private UUID resolvedBy;
    @Column(name = "resolved_at")
    private Instant resolvedAt;
    @Version
    private Long version;

    public static TrainProvAssessmentModeration create(TenantId t, String no, UUID attemptId, UUID moderatorId, UUID appointmentId, LocalDate on,
                                                       String outcome, String basis, UUID by) {
        TrainProvAssessmentModeration m = new TrainProvAssessmentModeration();
        m.tenantId = t.getValue();
        m.moderationNo = no;
        m.attemptId = attemptId;
        m.moderatorId = moderatorId;
        m.appointmentId = appointmentId;
        m.moderatedOn = on;
        m.outcome = outcome;
        m.basis = basis.trim();
        m.enteredBy = by;
        m.createdAt = Instant.now();
        return m;
    }

    public boolean isResolved() {
        return resolvedAt != null;
    }

    public void resolve(String note, UUID by) {
        String problem = ModerationRules.resolveProblem(outcome, isResolved(), note);
        if (problem != null) {
            throw new IllegalStateException(problem);
        }
        this.resolutionNote = note.trim();
        this.resolvedBy = by;
        this.resolvedAt = Instant.now();
    }
}

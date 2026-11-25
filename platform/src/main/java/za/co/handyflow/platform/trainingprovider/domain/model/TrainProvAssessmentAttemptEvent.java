package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One line of an attempt's history. Written once, never changed. */
@Entity(name = "TrainProvAssessmentAttemptEvent")
@Table(name = "trainprov_assessment_attempt_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvAssessmentAttemptEvent {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "attempt_id", nullable = false, updatable = false)
    private UUID attemptId;
    @Column(nullable = false, updatable = false)
    private String action;
    @Column(updatable = false)
    private String note;
    @Column(name = "acted_by", nullable = false, updatable = false)
    private UUID actedBy;
    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt;

    public static TrainProvAssessmentAttemptEvent of(TenantId t, UUID attemptId, String action, String note, UUID by) {
        TrainProvAssessmentAttemptEvent e = new TrainProvAssessmentAttemptEvent();
        e.tenantId = t.getValue();
        e.attemptId = attemptId;
        e.action = action;
        e.note = note == null || note.isBlank() ? null : note.trim();
        e.actedBy = by;
        e.actedAt = Instant.now();
        return e;
    }
}

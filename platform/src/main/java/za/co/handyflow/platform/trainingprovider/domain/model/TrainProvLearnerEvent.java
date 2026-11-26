package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One change to a learner's delegate link, or one merge. Written once, never changed. */
@Entity(name = "TrainProvLearnerEvent")
@Table(name = "trainprov_learner_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvLearnerEvent {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "learner_id", nullable = false, updatable = false)
    private UUID learnerId;
    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;
    @Column(name = "delegate_id", updatable = false)
    private UUID delegateId;
    @Column(name = "other_learner_id", updatable = false)
    private UUID otherLearnerId;
    @Column(updatable = false)
    private String reason;
    @Column(name = "acted_by", nullable = false, updatable = false)
    private UUID actedBy;
    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt;

    public static TrainProvLearnerEvent delegate(TenantId t, UUID learnerId, boolean linked, UUID delegateId, String reason, UUID by) {
        TrainProvLearnerEvent e = base(t, learnerId, linked ? "DELEGATE_LINKED" : "DELEGATE_UNLINKED", reason, by);
        e.delegateId = delegateId;
        return e;
    }

    public static TrainProvLearnerEvent merge(TenantId t, UUID learnerId, boolean mergedInto, UUID otherLearnerId, String reason, UUID by) {
        TrainProvLearnerEvent e = base(t, learnerId, mergedInto ? "MERGED_INTO" : "MERGED_FROM", reason, by);
        e.otherLearnerId = otherLearnerId;
        return e;
    }

    private static TrainProvLearnerEvent base(TenantId t, UUID learnerId, String type, String reason, UUID by) {
        TrainProvLearnerEvent e = new TrainProvLearnerEvent();
        e.tenantId = t.getValue();
        e.learnerId = learnerId;
        e.eventType = type;
        e.reason = reason == null || reason.isBlank() ? null : reason.trim();
        e.actedBy = by;
        e.actedAt = Instant.now();
        return e;
    }
}

package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One line of an enrolment's history. Written once, never changed. */
@Entity(name = "TrainProvLearnerEnrolmentEvent")
@Table(name = "trainprov_learner_enrolment_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvLearnerEnrolmentEvent {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "enrolment_id", nullable = false, updatable = false)
    private UUID enrolmentId;
    @Column(name = "from_status", updatable = false)
    private String fromStatus;
    @Column(name = "to_status", nullable = false, updatable = false)
    private String toStatus;
    @Column(updatable = false)
    private String reason;
    @Column(name = "acted_by", nullable = false, updatable = false)
    private UUID actedBy;
    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt;

    public static TrainProvLearnerEnrolmentEvent of(TenantId t, UUID enrolmentId, String from, String to, String reason, UUID by) {
        TrainProvLearnerEnrolmentEvent e = new TrainProvLearnerEnrolmentEvent();
        e.tenantId = t.getValue();
        e.enrolmentId = enrolmentId;
        e.fromStatus = from;
        e.toStatus = to;
        e.reason = reason == null || reason.isBlank() ? null : reason.trim();
        e.actedBy = by;
        e.actedAt = Instant.now();
        return e;
    }
}

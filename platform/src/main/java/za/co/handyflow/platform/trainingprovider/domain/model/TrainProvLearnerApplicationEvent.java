package za.co.handyflow.platform.trainingprovider.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One line of an application's history. Written once, never changed. */
@Entity(name = "TrainProvLearnerApplicationEvent")
@Table(name = "trainprov_learner_application_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrainProvLearnerApplicationEvent {
    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;
    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;
    @Column(name = "from_status", updatable = false)
    private String fromStatus;
    @Column(name = "to_status", nullable = false, updatable = false)
    private String toStatus;
    @Column(updatable = false)
    private String reason;
    @Column(name = "override_reason", updatable = false)
    private String overrideReason;
    @Column(name = "acted_by", nullable = false, updatable = false)
    private UUID actedBy;
    @Column(name = "acted_at", nullable = false, updatable = false)
    private Instant actedAt;

    public static TrainProvLearnerApplicationEvent of(TenantId t, UUID applicationId, String from, String to, String reason, String override, UUID by) {
        TrainProvLearnerApplicationEvent e = new TrainProvLearnerApplicationEvent();
        e.tenantId = t.getValue();
        e.applicationId = applicationId;
        e.fromStatus = from;
        e.toStatus = to;
        e.reason = reason == null || reason.isBlank() ? null : reason.trim();
        e.overrideReason = override == null || override.isBlank() ? null : override.trim();
        e.actedBy = by;
        e.actedAt = Instant.now();
        return e;
    }
}

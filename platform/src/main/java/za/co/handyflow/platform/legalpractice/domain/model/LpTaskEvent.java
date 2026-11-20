package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Who did what to a task, and when. Append-only. */
@Entity
@Table(name = "lp_task_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpTaskEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "task_id", nullable = false, updatable = false) private UUID taskId;
    @Column(name = "matter_id", nullable = false, updatable = false) private UUID matterId;
    @Column(name = "event_type", nullable = false, updatable = false, length = 20) private String eventType;
    @Column(name = "actor_id", nullable = false, updatable = false) private UUID actorId;
    @Column(name = "actor_name", updatable = false) private String actorName;
    @Column(updatable = false, length = 1200) private String detail;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;

    public static LpTaskEvent record(TenantId tenantId, UUID taskId, UUID matterId, String type, UUID actor, String actorName, String detail, Instant now) {
        LpTaskEvent e = new LpTaskEvent();
        e.tenantId = tenantId; e.taskId = taskId; e.matterId = matterId; e.eventType = type; e.actorId = actor; e.actorName = actorName; e.occurredAt = now;
        e.detail = detail == null ? null : detail.length() > 1200 ? detail.substring(0, 1197) + "..." : detail;
        return e;
    }
}

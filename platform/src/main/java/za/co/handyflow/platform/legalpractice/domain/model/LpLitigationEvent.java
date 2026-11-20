package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Who did what to a case, a hearing or an order, and when. Append-only. The stage history is the STAGE_CHANGED rows. */
@Entity
@Table(name = "lp_litigation_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpLitigationEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "case_id", nullable = false, updatable = false) private UUID caseId;
    @Column(name = "matter_id", nullable = false, updatable = false) private UUID matterId;
    @Column(name = "event_type", nullable = false, updatable = false, length = 20) private String eventType;
    @Column(name = "actor_id", nullable = false, updatable = false) private UUID actorId;
    @Column(name = "actor_name", updatable = false) private String actorName;
    @Column(updatable = false) private String detail;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;

    public static LpLitigationEvent record(TenantId tenantId, UUID caseId, UUID matterId, String type, UUID actor, String actorName, String detail, Instant now) {
        LpLitigationEvent e = new LpLitigationEvent();
        e.tenantId = tenantId; e.caseId = caseId; e.matterId = matterId; e.eventType = type; e.actorId = actor; e.actorName = actorName; e.detail = detail; e.occurredAt = now;
        return e;
    }
}

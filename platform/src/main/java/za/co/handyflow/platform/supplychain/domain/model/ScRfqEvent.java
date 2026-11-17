package za.co.handyflow.platform.supplychain.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Append-only RFQ audit trail — who published, extended, scored, awarded and why. */
@Entity
@Table(name = "sc_rfq_events")
@Getter
@NoArgsConstructor
public class ScRfqEvent {

    @Id UUID id;
    @Column(name = "tenant_id", nullable = false) UUID tenantId;
    @Column(name = "rfq_id", nullable = false) UUID rfqId;
    @Column(name = "event_type", nullable = false, length = 30) String eventType;
    @Column(length = 1000) String detail;
    UUID actor;
    @Column(name = "actor_name", length = 255) String actorName;
    @Column(name = "created_at", nullable = false) Instant createdAt;

    public static ScRfqEvent of(UUID tenantId, UUID rfqId, String type, String detail, UUID actor, String actorName) {
        ScRfqEvent e = new ScRfqEvent();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.rfqId = rfqId;
        e.eventType = type;
        e.detail = detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail;
        e.actor = actor;
        e.actorName = actorName;
        e.createdAt = Instant.now();
        return e;
    }
}
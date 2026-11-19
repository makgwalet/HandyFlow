package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Append-only audit trail for the regulatory library and applicability decisions. */
@Entity
@Table(name = "legalcompliance_regulatory_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegulatoryEvent {

    @Id private UUID id;
    @Column(name = "tenant_id") private UUID tenantId;
    @Column(name = "entity_type", nullable = false, length = 40) private String entityType;
    @Column(name = "entity_id", nullable = false) private UUID entityId;
    @Column(name = "event_type", nullable = false, length = 50) private String eventType;
    @Column(columnDefinition = "TEXT") private String detail;
    @Column(name = "actor_id") private UUID actorId;
    @Column(name = "actor_name", length = 255) private String actorName;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static RegulatoryEvent of(UUID tenantId, String entityType, UUID entityId, String eventType, String detail,
                                     UUID actorId, String actorName) {
        RegulatoryEvent e = new RegulatoryEvent();
        e.id = UUID.randomUUID();
        e.tenantId = tenantId;
        e.entityType = entityType;
        e.entityId = entityId;
        e.eventType = eventType;
        e.detail = detail;
        e.actorId = actorId;
        e.actorName = actorName;
        e.createdAt = Instant.now();
        return e;
    }
}

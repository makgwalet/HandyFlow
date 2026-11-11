package za.co.handyflow.platform.events.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One immutable line in an event's business history. Deliberately has no
 * mutators — rows are only ever created (and the database blocks UPDATE).
 * Complements event_check_ins, which keeps its own per-scan history.
 */
@Entity
@Table(name = "event_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventAuditRecord {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)   private UUID tenantId;
    @Column(name = "event_id", nullable = false, updatable = false)    private UUID eventId;
    @Column(name = "action", nullable = false, updatable = false)      private String action;
    @Column(name = "entity_type", nullable = false, updatable = false) private String entityType;
    @Column(name = "entity_id", updatable = false)                     private UUID entityId;
    @Column(name = "summary", nullable = false, updatable = false)     private String summary;
    @Column(name = "details", columnDefinition = "TEXT", updatable = false) private String details;
    @Column(name = "actor_id", updatable = false)                      private UUID actorId;
    @Column(name = "actor_name", nullable = false, updatable = false)  private String actorName;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;

    public static EventAuditRecord create(UUID tenantId, UUID eventId, String action,
                                          String entityType, UUID entityId, String summary,
                                          String details, UUID actorId, String actorName) {
        EventAuditRecord r = new EventAuditRecord();
        r.id          = UUID.randomUUID();
        r.tenantId    = tenantId;
        r.eventId     = eventId;
        r.action      = action;
        r.entityType  = entityType;
        r.entityId    = entityId;
        r.summary     = summary.length() > 500 ? summary.substring(0, 497) + "..." : summary;
        r.details     = details;
        r.actorId     = actorId;
        r.actorName   = actorName;
        r.occurredAt  = Instant.now();
        return r;
    }
}
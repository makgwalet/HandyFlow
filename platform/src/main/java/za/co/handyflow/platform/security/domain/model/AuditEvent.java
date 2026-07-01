// security/domain/model/AuditEvent.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * AuditEvent — maps to the security_audit_log table created in V103 (bug #22),
 * which had no JPA entity until now.
 *
 * Append-only, per the table's own DDL comment: no UPDATE, no DELETE, ever.
 * This entity has no mutator methods at all, by design — every field is set
 * once at construction.
 *
 * WHY a generic entity_type/action pair rather than a typed entity per
 * audited type?
 * The table was deliberately built this way (see V103 header) so that any
 * entity in the module — guards, sites, shifts, incidents, and now
 * principals — gets one query path for "who did what when" rather than a
 * separate audit table per entity type. This entity is the first JPA mapping
 * onto that table; Part 9.3's principal-read auditing is the first concrete
 * use of it, but it's intentionally not VIP-specific.
 *
 * oldValues/newValues/metadata are raw JSON strings (mapped to JSONB) rather
 * than typed objects — different entity types have completely different
 * shapes, and forcing a common structure would defeat the table's purpose.
 */
@Entity
@Table(name = "security_audit_log")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "actor_id")
    private UUID actorId;   // null for system-generated events (schedulers)

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private ActorType actorType = ActorType.USER;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;   // GUARD | SITE | SHIFT | INCIDENT | CHECKPOINT | SCAN | PRINCIPAL | ...

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 50)
    private String action;       // CREATED | UPDATED | DELETED | VIEWED | STATUS_CHANGED | ...

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    private String oldValues;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    private String newValues;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static AuditEvent record(TenantId tenantId, UUID actorId, ActorType actorType,
                                    String entityType, UUID entityId, String action,
                                    String oldValues, String newValues, String metadata) {
        AuditEvent e   = new AuditEvent();
        e.tenantId     = tenantId;
        e.actorId      = actorId;
        e.actorType    = actorType != null ? actorType : ActorType.USER;
        e.entityType   = entityType;
        e.entityId     = entityId;
        e.action       = action;
        e.oldValues    = oldValues;
        e.newValues    = newValues;
        e.metadata     = metadata;
        e.occurredAt   = Instant.now();
        return e;
    }

    /** Convenience factory for the common case: a user viewing a record (no before/after diff). */
    public static AuditEvent recordView(TenantId tenantId, UUID actorId,
                                        String entityType, UUID entityId, String metadata) {
        return record(tenantId, actorId, ActorType.USER, entityType, entityId,
                "VIEWED", null, null, metadata);
    }

    // ── Enum ───────────────────────────────────────────────────────────────────

    public enum ActorType {
        USER, SYSTEM, GUARD_APP
    }
}

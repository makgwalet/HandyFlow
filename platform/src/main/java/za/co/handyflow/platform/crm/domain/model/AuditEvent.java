package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** One line of the CRM audit log. Written once, never changed. */
@Entity
@Table(name = "crm_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 30)
    private AuditEntity entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "entity_name", length = 200)
    private String entityName;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private AuditAction action;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AuditEvent of(TenantId tenantId, AuditEntity type, UUID entityId, String name, AuditAction action, String detail, UUID actorId) {
        var e = new AuditEvent();
        e.tenantId = tenantId;
        e.entityType = type;
        e.entityId = entityId;
        e.entityName = name;
        e.action = action;
        e.detail = detail;
        e.actorId = actorId;
        e.createdAt = Instant.now();
        return e;
    }
}

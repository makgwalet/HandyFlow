package za.co.handyflow.platform.catalogue.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One line of the catalogue history. Written once, never changed. */
@Entity
@Table(name = "catalogue_audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogueAuditEvent {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "entity_name", length = 255)
    private String entityName;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private CatalogueAuditAction action;

    @Column(name = "detail", length = 1000)
    private String detail;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Running change number, given by the database. Used by the change feed. */
    @Column(name = "seq", insertable = false, updatable = false)
    private Long seq;

    /** Published by Spring Data when the line is saved, so other modules can react to item changes without the catalogue calling them. */
    @org.springframework.data.domain.DomainEvents
    java.util.Collection<Object> domainEvents() {
        return "ITEM".equals(entityType)
                ? java.util.List.of(new za.co.handyflow.platform.catalogue.CatalogueItemChanged(tenantId, entityId, action.name(), createdAt))
                : java.util.List.of();
    }

    public static CatalogueAuditEvent of(TenantId tenantId, String entityType, UUID entityId, String entityName,
                                         CatalogueAuditAction action, String detail, UUID actorId) {
        var e = new CatalogueAuditEvent();
        e.tenantId = tenantId;
        e.entityType = entityType;
        e.entityId = entityId;
        e.entityName = entityName;
        e.action = action;
        e.detail = detail;
        e.actorId = actorId;
        e.createdAt = Instant.now();
        return e;
    }
}

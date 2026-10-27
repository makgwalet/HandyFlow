package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Append-only record of a sensitive action. Immutable in Hibernate and protected by a DB trigger against UPDATE. */
@Entity
@Immutable
@Table(name = "facility_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilityAuditLog {

    private static final int MAX_DETAILS = 2000;

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(length = MAX_DETAILS)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static FacilityAuditLog of(TenantId tenantId, UUID actorUserId, String actorName, String action,
                                      String entityType, UUID entityId, String details) {
        FacilityAuditLog l = new FacilityAuditLog();
        l.tenantId = tenantId;
        l.actorUserId = actorUserId;
        l.actorName = actorName;
        l.action = action;
        l.entityType = entityType;
        l.entityId = entityId;
        l.details = details != null && details.length() > MAX_DETAILS ? details.substring(0, MAX_DETAILS) : details;
        l.createdAt = Instant.now();
        return l;
    }
}
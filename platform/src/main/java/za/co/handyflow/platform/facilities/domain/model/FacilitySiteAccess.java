package za.co.handyflow.platform.facilities.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Grants a user access to one site. A user with at least one row is
 * restricted to exactly those sites; a user with none is unrestricted
 * (backward compatible). {@code userId} is an unvalidated reference to a
 * system user, like {@code FacilityTechnician.linkedUserId}.
 */
@Entity
@Table(name = "facility_site_access")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FacilitySiteAccess {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "granted_by")
    private UUID grantedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static FacilitySiteAccess grant(TenantId tenantId, UUID userId, UUID siteId, UUID grantedBy) {
        FacilitySiteAccess a = new FacilitySiteAccess();
        a.tenantId = tenantId;
        a.userId = userId;
        a.siteId = siteId;
        a.grantedBy = grantedBy;
        a.createdAt = Instant.now();
        return a;
    }
}
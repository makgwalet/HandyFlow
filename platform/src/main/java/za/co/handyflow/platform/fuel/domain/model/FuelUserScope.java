package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Phase 8c: one limit on a user's Fuel access, either a division or a depot (never both). A user with no rows is unrestricted. */
@Entity
@Table(name = "fuel_user_scopes")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelUserScope {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, updatable = false)
    private FuelDivision division;

    @Column(name = "depot_id", updatable = false)
    private UUID depotId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static FuelUserScope division(TenantId tenantId, UUID userId, FuelDivision division, UUID by) {
        if (division == null) throw new IllegalArgumentException("A division is needed.");
        FuelUserScope s = new FuelUserScope();
        s.tenantId = tenantId; s.userId = userId; s.division = division; s.createdBy = by;
        return s;
    }

    public static FuelUserScope depot(TenantId tenantId, UUID userId, UUID depotId, UUID by) {
        if (depotId == null) throw new IllegalArgumentException("A depot is needed.");
        FuelUserScope s = new FuelUserScope();
        s.tenantId = tenantId; s.userId = userId; s.depotId = depotId; s.createdBy = by;
        return s;
    }
}

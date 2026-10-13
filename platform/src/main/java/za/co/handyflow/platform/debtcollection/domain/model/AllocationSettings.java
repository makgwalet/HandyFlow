package za.co.handyflow.platform.debtcollection.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

/**
 * One row per tenant. Automatic allocation is OPT-IN (enabled=false until a supervisor turns it on after reviewing a
 * preview); manual allocation and previews always work. reallocateUnavailable: when auto-allocation runs, also move
 * cases away from collectors who are inactive or away.
 */
@Entity
@Table(name = "debtcollection_allocation_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AllocationSettings extends AggregateRoot<AllocationSettings> {

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_strategy", nullable = false, length = 15)
    private AllocationStrategy defaultStrategy;

    @Column(name = "reallocate_unavailable", nullable = false)
    private boolean reallocateUnavailable;

    public static AllocationSettings defaults(TenantId tenantId) {
        AllocationSettings s = new AllocationSettings();
        s.initTenantId(tenantId);
        s.enabled = false;
        s.defaultStrategy = AllocationStrategy.LEAST_LOADED;
        s.reallocateUnavailable = true;
        return s;
    }

    public void update(boolean enabled, AllocationStrategy defaultStrategy, boolean reallocateUnavailable) {
        this.enabled = enabled;
        this.defaultStrategy = defaultStrategy != null ? defaultStrategy : AllocationStrategy.LEAST_LOADED;
        this.reallocateUnavailable = reallocateUnavailable;
    }
}

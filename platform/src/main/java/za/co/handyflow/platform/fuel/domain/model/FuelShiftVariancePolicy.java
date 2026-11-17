package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** How many out-of-tolerance shifts by one attendant, within how many days, raise an investigation alert. The numbers are the company's own. */
@Entity
@Table(name = "fuel_shift_variance_policy")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelShiftVariancePolicy {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "repeat_count", nullable = false)
    private int repeatCount;

    @Column(name = "repeat_days", nullable = false)
    private int repeatDays;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static FuelShiftVariancePolicy of(TenantId tenant, int count, int days, UUID by) {
        FuelShiftVariancePolicy p = new FuelShiftVariancePolicy();
        p.tenantId = tenant.getValue();
        p.set(count, days, by);
        return p;
    }

    public void set(int count, int days, UUID by) {
        if (count < 2) throw new IllegalArgumentException("Repeated means at least 2 shifts.");
        if (days < 1 || days > 365) throw new IllegalArgumentException("Choose between 1 and 365 days.");
        this.repeatCount = count; this.repeatDays = days; this.updatedBy = by; this.updatedAt = Instant.now();
    }
}

package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** Audit of a load allocated with eligibility review points acknowledged: who approved it, why, and what was open. Append only. */
@Entity
@Table(name = "fuel_eligibility_overrides")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelEligibilityOverride {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "tanker_id", nullable = false, updatable = false)
    private UUID tankerId;

    @Column(name = "driver_employee_id", nullable = false, updatable = false)
    private UUID driverEmployeeId;

    @Column(nullable = false, updatable = false, length = 2000)
    private String findings;

    @Column(nullable = false, updatable = false, length = 300)
    private String reason;

    @Column(name = "approved_by", nullable = false, updatable = false)
    private UUID approvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public static FuelEligibilityOverride of(TenantId tenantId, UUID deliveryId, UUID tankerId, UUID driverEmployeeId, String findings, String reason, UUID by) {
        FuelEligibilityOverride o = new FuelEligibilityOverride();
        o.tenantId = tenantId; o.deliveryId = deliveryId; o.tankerId = tankerId; o.driverEmployeeId = driverEmployeeId;
        o.findings = findings.length() > 2000 ? findings.substring(0, 2000) : findings;
        o.reason = reason.length() > 300 ? reason.substring(0, 300) : reason; o.approvedBy = by;
        return o;
    }
}

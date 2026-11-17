package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** One arrival eligibility check made by the driver, kept as a record of what was shown and when. */
@Entity
@Table(name = "fuel_arrival_checks")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelArrivalCheck {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "delivery_id", nullable = false, updatable = false)
    private UUID deliveryId;

    @Column(name = "checked_by", updatable = false)
    private UUID checkedBy;

    @Column(nullable = false, updatable = false)
    private boolean allowed;

    /** The findings as JSON, exactly as shown to the driver. */
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String findings;

    @Column(name = "checked_at", nullable = false, updatable = false)
    private Instant checkedAt = Instant.now();

    public static FuelArrivalCheck record(TenantId tenantId, UUID deliveryId, UUID by, boolean allowed, String findingsJson) {
        FuelArrivalCheck c = new FuelArrivalCheck();
        c.tenantId = tenantId; c.deliveryId = deliveryId; c.checkedBy = by; c.allowed = allowed; c.findings = findingsJson;
        return c;
    }
}

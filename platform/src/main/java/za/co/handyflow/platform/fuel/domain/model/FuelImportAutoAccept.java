package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A company's choice to accept clean imports automatically. Off by default; the gauge limit is the company's own number. */
@Entity
@Table(name = "fuel_import_auto_accept")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelImportAutoAccept {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false, updatable = false))
    private TenantId tenantId;

    @Column(name = "shifts_enabled", nullable = false)
    private boolean shiftsEnabled;

    @Column(name = "gauges_enabled", nullable = false)
    private boolean gaugesEnabled;

    @Column(name = "gauge_max_difference_litres", precision = 14, scale = 2)
    private BigDecimal gaugeMaxDifferenceLitres;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static FuelImportAutoAccept forTenant(TenantId tenantId) {
        FuelImportAutoAccept a = new FuelImportAutoAccept();
        a.tenantId = tenantId;
        return a;
    }

    /** Gauge readings are only accepted automatically against a limit the company has entered. */
    public void update(boolean shifts, boolean gauges, BigDecimal maxDifference, UUID by) {
        if (gauges && (maxDifference == null || maxDifference.signum() <= 0)) {
            throw new IllegalArgumentException("Enter the largest difference from the book level (in litres) a gauge reading may have to be accepted automatically.");
        }
        this.shiftsEnabled = shifts; this.gaugesEnabled = gauges;
        this.gaugeMaxDifferenceLitres = maxDifference; this.updatedBy = by; this.updatedAt = Instant.now();
    }
}

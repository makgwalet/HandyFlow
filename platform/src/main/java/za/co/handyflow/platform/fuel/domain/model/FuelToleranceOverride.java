package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A tolerance a company has set for one depot (cash variance, rand) or one fuel type (gauge difference, litres), in place of its company-wide number. */
@Entity
@Table(name = "fuel_tolerance_overrides")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelToleranceOverride {

    public static final String DEPOT = "DEPOT", PRODUCT = "PRODUCT";

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false, updatable = false))
    private TenantId tenantId;

    @Column(nullable = false, updatable = false, length = 10)
    private String scope;

    @Column(name = "scope_key", nullable = false, updatable = false, length = 60)
    private String scopeKey;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static FuelToleranceOverride of(TenantId tenant, String scope, String key, BigDecimal amount, UUID by) {
        FuelToleranceOverride o = new FuelToleranceOverride();
        o.tenantId = tenant; o.scope = scope; o.scopeKey = key;
        o.set(amount, by);
        return o;
    }

    public void set(BigDecimal amount, UUID by) {
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("The tolerance cannot be negative.");
        this.amount = amount.setScale(2, java.math.RoundingMode.HALF_UP); this.updatedBy = by; this.updatedAt = Instant.now();
    }
}

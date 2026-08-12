package za.co.handyflow.platform.pos.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pos_settings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PosSettings {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false, unique = true))
    private TenantId tenantId;

    @Column(name = "cash_variance_tolerance_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal cashVarianceToleranceAmount;

    @Column(name = "cash_variance_tolerance_pct", nullable = false, precision = 5, scale = 4)
    private BigDecimal cashVarianceTolerancePct;

    @Column(name = "cash_variance_critical_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal cashVarianceCriticalAmount;

    @Column(name = "cash_variance_critical_pct", nullable = false, precision = 5, scale = 4)
    private BigDecimal cashVarianceCriticalPct;

    @Column(name = "created_at") private Instant createdAt;
    @Column(name = "updated_at") private Instant updatedAt;

    // Defaults: R20 flat floor OR 1% of expected cash (whichever is
    // greater) absorbs till-counting noise without alerting; beyond R200
    // or 5% (whichever is greater) escalates to CRITICAL instead of
    // WARNING. See PosService.evaluateCashVariance() for how these four
    // numbers combine — tenant admin can retune all four via Settings.
    public static PosSettings defaults(TenantId tenantId) {
        PosSettings s = new PosSettings();
        s.tenantId = tenantId;
        s.cashVarianceToleranceAmount = new BigDecimal("20.00");
        s.cashVarianceTolerancePct    = new BigDecimal("0.0100");
        s.cashVarianceCriticalAmount  = new BigDecimal("200.00");
        s.cashVarianceCriticalPct     = new BigDecimal("0.0500");
        s.createdAt = Instant.now();
        s.updatedAt = Instant.now();
        return s;
    }

    public void update(BigDecimal toleranceAmount, BigDecimal tolerancePct,
                       BigDecimal criticalAmount, BigDecimal criticalPct) {
        if (toleranceAmount != null) this.cashVarianceToleranceAmount = toleranceAmount;
        if (tolerancePct    != null) this.cashVarianceTolerancePct    = tolerancePct;
        if (criticalAmount  != null) this.cashVarianceCriticalAmount  = criticalAmount;
        if (criticalPct     != null) this.cashVarianceCriticalPct     = criticalPct;
        this.updatedAt = Instant.now();
    }
}
package za.co.handyflow.platform.compliancetender.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The markups and VAT treatment for one tender's price (ADR-004). One row per tender. Amounts are never stored here: they are computed from the lines
 * and these settings by TenderPriceCalculator. vatRatePct is the rate in force when pricing was first created, so the price does not move if the rate changes.
 */
@Entity
@Table(name = "tender_pricing")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenderPricing {

    private static final BigDecimal MAX_PCT = BigDecimal.valueOf(100);

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "tender_id", nullable = false)
    private UUID tenderId;

    @Column(name = "overhead_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal overheadPct = BigDecimal.ZERO;

    @Column(name = "contingency_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal contingencyPct = BigDecimal.ZERO;

    @Column(name = "profit_pct", nullable = false, precision = 6, scale = 2)
    private BigDecimal profitPct = BigDecimal.ZERO;

    @Column(name = "vat_applies", nullable = false)
    private boolean vatApplies = true;

    @Column(name = "vat_rate_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRatePct;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Version
    private Long version;

    public static TenderPricing create(TenantId tenantId, UUID tenderId, BigDecimal vatRatePct, UUID createdBy) {
        if (vatRatePct == null) throw new IllegalArgumentException("vatRatePct is required");
        TenderPricing p = new TenderPricing();
        p.tenantId = tenantId;
        p.tenderId = tenderId;
        p.vatRatePct = vatRatePct;
        p.createdAt = Instant.now();
        p.createdBy = createdBy;
        p.updatedAt = p.createdAt;
        p.updatedBy = createdBy;
        return p;
    }

    public void update(BigDecimal overheadPct, BigDecimal contingencyPct, BigDecimal profitPct,
                       boolean vatApplies, String notes, UUID updatedBy) {
        this.overheadPct = pct("overheadPct", overheadPct);
        this.contingencyPct = pct("contingencyPct", contingencyPct);
        this.profitPct = pct("profitPct", profitPct);
        this.vatApplies = vatApplies;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    private static BigDecimal pct(String field, BigDecimal v) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0 || v.compareTo(MAX_PCT) > 0) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
        return v;
    }
}

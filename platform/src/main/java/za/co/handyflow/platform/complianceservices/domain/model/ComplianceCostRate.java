package za.co.handyflow.platform.complianceservices.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** What an hour of one person costs the practice from a given day. Never changed: a later rate takes over. */
@Entity(name = "ComplianceCostRate")
@Table(name = "compliance_cost_rates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ComplianceCostRate {
    @Id
    private UUID id = UUID.randomUUID();
    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "hourly_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal hourlyCost;
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;
    @Column(name = "set_by", nullable = false)
    private UUID setBy;
    @Column(name = "set_at", nullable = false, updatable = false)
    private Instant setAt;
    @Version
    private Long version;

    public static ComplianceCostRate of(TenantId tenantId, UUID userId, BigDecimal hourlyCost, LocalDate effectiveFrom, UUID by) {
        if (userId == null) throw new IllegalArgumentException("Choose the person this rate is for");
        if (effectiveFrom == null) throw new IllegalArgumentException("Say from which day this rate applies");
        if (by == null) throw new IllegalArgumentException("Someone must record this");
        ComplianceCostRate r = new ComplianceCostRate();
        r.tenantId = tenantId;
        r.userId = userId;
        r.hourlyCost = ProfitRules.checkRate(hourlyCost);
        r.effectiveFrom = effectiveFrom;
        r.setBy = by;
        r.setAt = Instant.now();
        return r;
    }
}

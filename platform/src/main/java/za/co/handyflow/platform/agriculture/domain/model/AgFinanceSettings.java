package za.co.handyflow.platform.agriculture.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.agriculture.domain.rules.AgLabourRules;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row per tenant: the two numbers labour costing needs (ADR-001, W3). With no row the defaults in {@link AgLabourRules} apply. */
@Entity
@Table(name = "ag_finance_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgFinanceSettings {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "standard_hours_per_week", nullable = false, precision = 5, scale = 2)
    private BigDecimal standardHoursPerWeek;

    @Column(name = "labour_on_cost_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal labourOnCostPercent;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static AgFinanceSettings create(TenantId tenantId, BigDecimal standardHoursPerWeek, BigDecimal labourOnCostPercent, UUID updatedBy) {
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required");
        AgLabourRules.requireValidSettings(standardHoursPerWeek, labourOnCostPercent);
        AgFinanceSettings s = new AgFinanceSettings();
        s.tenantId = tenantId;
        s.standardHoursPerWeek = standardHoursPerWeek;
        s.labourOnCostPercent = labourOnCostPercent;
        s.updatedBy = updatedBy;
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return s;
    }

    /** Changes apply to labour costed from now on; costs already in the ledger keep the rate they were costed at. */
    public void update(BigDecimal standardHoursPerWeek, BigDecimal labourOnCostPercent, UUID updatedBy) {
        AgLabourRules.requireValidSettings(standardHoursPerWeek, labourOnCostPercent);
        this.standardHoursPerWeek = standardHoursPerWeek;
        this.labourOnCostPercent = labourOnCostPercent;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}

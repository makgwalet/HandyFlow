package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** Per-tenant risk appetite and tolerance thresholds (scores out of 25). Defaults: appetite 9, tolerance 16. */
@Entity
@Table(name = "legalcompliance_risk_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RiskSettings extends AggregateRoot<RiskSettings> {

    @Column(name = "appetite_max", nullable = false) private int appetiteMax;
    @Column(name = "tolerance_max", nullable = false) private int toleranceMax;
    @Column(name = "updated_by") private UUID updatedBy;

    public static RiskSettings defaults(TenantId tenantId) {
        RiskSettings s = new RiskSettings();
        s.initTenantId(tenantId);
        s.appetiteMax = 9;
        s.toleranceMax = 16;
        return s;
    }

    public void update(int appetiteMax, int toleranceMax, UUID by) {
        za.co.handyflow.platform.legalcompliance.application.internal.RiskScoringRules.validateThresholds(appetiteMax, toleranceMax);
        this.appetiteMax = appetiteMax;
        this.toleranceMax = toleranceMax;
        this.updatedBy = by;
    }
}

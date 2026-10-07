// security/domain/model/GuardRiskSettings.java
package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.security.application.internal.GuardRiskEngine;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;

/** A tenant's thresholds for the risk recommendations. One row per tenant; defaults apply until one is saved. */
@Entity
@Table(name = "security_risk_settings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class GuardRiskSettings {

    @Id
    private java.util.UUID id = java.util.UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "review_at", nullable = false) private int reviewAt = 1;
    @Column(name = "warning_at", nullable = false) private int warningAt = 3;
    @Column(name = "investigation_at", nullable = false) private int investigationAt = 5;
    @Column(name = "window_days", nullable = false) private int windowDays = 90;
    @Column(name = "misconduct_at", nullable = false) private int misconductAt = 2;
    @Column(name = "misconduct_window_days", nullable = false) private int misconductWindowDays = 365;
    @Column(name = "suspension_review_on_critical", nullable = false) private boolean suspensionReviewOnCritical = true;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    @Column(name = "updated_by_name", length = 200) private String updatedByName;

    public static GuardRiskSettings forTenant(TenantId tenantId) {
        GuardRiskSettings s = new GuardRiskSettings();
        s.tenantId = tenantId;
        return s;
    }

    public void apply(GuardRiskEngine.Settings v, String byName) {
        reviewAt = v.reviewAt(); warningAt = v.warningAt(); investigationAt = v.investigationAt(); windowDays = v.windowDays();
        misconductAt = v.misconductAt(); misconductWindowDays = v.misconductWindowDays();
        suspensionReviewOnCritical = v.suspensionReviewOnCritical();
        updatedAt = Instant.now(); updatedByName = byName;
    }

    public GuardRiskEngine.Settings toSettings() {
        return new GuardRiskEngine.Settings(reviewAt, warningAt, investigationAt, windowDays, misconductAt, misconductWindowDays, suspensionReviewOnCritical);
    }
}

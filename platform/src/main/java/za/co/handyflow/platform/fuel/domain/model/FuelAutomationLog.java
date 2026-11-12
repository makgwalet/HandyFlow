package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** What an automation run did for one rule. Append-only. */
@Entity
@Table(name = "fuel_automation_log")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelAutomationLog {

    @Id private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "rule_id", nullable = false, updatable = false) private UUID ruleId;
    @Column(nullable = false, updatable = false, length = 12) private String outcome;
    @Column(length = 300, updatable = false) private String detail;
    @Column(name = "requisition_id", updatable = false) private UUID requisitionId;
    @Column(name = "ran_at", nullable = false, updatable = false) private Instant ranAt = Instant.now();

    public static FuelAutomationLog drafted(TenantId tenantId, UUID ruleId, String detail, UUID requisitionId) { return of(tenantId, ruleId, "DRAFTED", detail, requisitionId); }
    public static FuelAutomationLog failed(TenantId tenantId, UUID ruleId, String detail) { return of(tenantId, ruleId, "FAILED", detail, null); }

    private static FuelAutomationLog of(TenantId tenantId, UUID ruleId, String outcome, String detail, UUID requisitionId) {
        FuelAutomationLog l = new FuelAutomationLog();
        l.tenantId = tenantId; l.ruleId = ruleId; l.outcome = outcome; l.requisitionId = requisitionId;
        l.detail = detail == null ? null : (detail.length() > 300 ? detail.substring(0, 300) : detail);
        return l;
    }
}

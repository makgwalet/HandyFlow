package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** One time a journey step ran for a person. */
@Entity
@Table(name = "crm_journey_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JourneyRun {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "step_position", nullable = false)
    private int stepPosition;

    @Column(name = "outcome", nullable = false, length = 10)
    private String outcome;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "ran_at", nullable = false)
    private Instant ranAt;

    public static JourneyRun of(TenantId tenantId, UUID campaignId, UUID customerId, int stepPosition, boolean done, String detail) {
        var r = new JourneyRun();
        r.tenantId = tenantId;
        r.campaignId = campaignId;
        r.customerId = customerId;
        r.stepPosition = stepPosition;
        r.outcome = done ? "DONE" : "FAILED";
        r.detail = detail == null ? null : detail.length() > 500 ? detail.substring(0, 499) + "…" : detail;
        r.ranAt = Instant.now();
        return r;
    }
}

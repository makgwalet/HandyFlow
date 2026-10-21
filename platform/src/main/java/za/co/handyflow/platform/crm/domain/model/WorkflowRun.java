package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** One time a workflow ran for a lead. */
@Entity
@Table(name = "crm_workflow_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowRun {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "outcome", nullable = false, length = 10)
    private String outcome;

    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "ran_at", nullable = false)
    private Instant ranAt;

    public static WorkflowRun of(TenantId tenantId, UUID workflowId, UUID customerId, boolean done, String detail) {
        var r = new WorkflowRun();
        r.tenantId = tenantId;
        r.workflowId = workflowId;
        r.customerId = customerId;
        r.outcome = done ? "DONE" : "FAILED";
        r.detail = detail == null ? null : detail.length() > 500 ? detail.substring(0, 500) : detail;
        r.ranAt = Instant.now();
        return r;
    }
}

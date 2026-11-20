package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A workflow applied to a matter. A workflow can be applied to a given matter once. Never changed or deleted. */
@Entity
@Table(name = "lp_workflow_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpWorkflowRun {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "template_id", nullable = false, updatable = false) private UUID templateId;
    @Column(name = "template_name", nullable = false, updatable = false, length = 120) private String templateName;
    @Column(name = "matter_id", nullable = false, updatable = false) private UUID matterId;
    @Column(name = "start_date", nullable = false, updatable = false) private LocalDate startDate;
    @Column(name = "tasks_created", nullable = false, updatable = false) private int tasksCreated;
    @Column(name = "applied_by", nullable = false, updatable = false) private UUID appliedBy;
    @Column(name = "applied_by_name", updatable = false) private String appliedByName;
    @Column(name = "applied_at", nullable = false, updatable = false) private Instant appliedAt;

    public static LpWorkflowRun of(TenantId tenantId, UUID templateId, String templateName, UUID matterId, LocalDate start, int count, UUID by, String byName, Instant now) {
        LpWorkflowRun r = new LpWorkflowRun();
        r.tenantId = tenantId; r.templateId = templateId; r.templateName = templateName; r.matterId = matterId; r.startDate = start;
        r.tasksCreated = count; r.appliedBy = by; r.appliedByName = byName; r.appliedAt = now;
        return r;
    }
}

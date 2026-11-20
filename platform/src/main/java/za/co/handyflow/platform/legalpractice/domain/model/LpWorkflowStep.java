package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** One step of a workflow: becomes a task due {@code offsetDays} after the start date. */
@Entity
@Table(name = "lp_workflow_steps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpWorkflowStep {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "template_id", nullable = false, updatable = false) private UUID templateId;
    @Column(name = "step_no", nullable = false) private int stepNo;
    @Column(nullable = false, length = 200) private String title;
    @Column(length = 2000) private String description;
    @Column(nullable = false, length = 10) private String priority = "NORMAL";
    @Column(name = "offset_days", nullable = false) private int offsetDays;
    @Column(name = "assign_to", nullable = false, length = 10) private String assignTo = "STARTER";

    public static LpWorkflowStep of(TenantId tenantId, UUID templateId, int stepNo, String title, String description, String priority, int offsetDays, String assignTo) {
        LpWorkflowStep s = new LpWorkflowStep();
        s.tenantId = tenantId; s.templateId = templateId; s.stepNo = stepNo; s.title = title.trim();
        s.description = description == null || description.isBlank() ? null : description.trim();
        s.priority = priority; s.offsetDays = offsetDays; s.assignTo = assignTo;
        return s;
    }
}

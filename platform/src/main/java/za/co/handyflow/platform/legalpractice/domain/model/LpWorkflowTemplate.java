package za.co.handyflow.platform.legalpractice.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.legalpractice.application.internal.TaskRules;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A named list of steps that can be applied to a matter to create its tasks. Turned off, not deleted. */
@Entity
@Table(name = "lp_workflow_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LpWorkflowTemplate {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(nullable = false, length = 120) private String name;
    @Column(length = 1000) private String description;
    @Column(name = "matter_type", length = 50) private String matterType;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_by", nullable = false, updatable = false) private UUID createdBy;
    @Column(name = "created_by_name", updatable = false) private String createdByName;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @Version
    private Long version;

    public static LpWorkflowTemplate create(TenantId tenantId, String name, String description, String matterType, UUID by, String byName, Instant now) {
        String problem = TaskRules.templateProblem(name, description, matterType);
        if (problem != null) throw new IllegalArgumentException(problem);
        if (by == null) throw new IllegalArgumentException("A workflow must say who created it");
        LpWorkflowTemplate t = new LpWorkflowTemplate();
        t.tenantId = tenantId; t.createdBy = by; t.createdByName = byName; t.createdAt = now;
        t.apply(name, description, matterType, now);
        return t;
    }

    public void edit(String name, String description, String matterType, Instant now) {
        String problem = TaskRules.templateProblem(name, description, matterType);
        if (problem != null) throw new IllegalArgumentException(problem);
        apply(name, description, matterType, now);
    }

    public void setActive(boolean on, Instant now) {
        if (active == on) throw new IllegalStateException(on ? "This workflow is already on" : "This workflow is already off");
        active = on; updatedAt = now;
    }

    private void apply(String name, String description, String matterType, Instant now) {
        this.name = name.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        this.matterType = matterType == null || matterType.isBlank() ? null : matterType.trim();
        this.updatedAt = now;
    }
}

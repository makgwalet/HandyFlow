package za.co.handyflow.platform.crm.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

/** "When this happens (and the lead fits), do this." */
@Entity
@Table(name = "crm_workflows")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workflow {
    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_event", nullable = false, length = 30)
    private WorkflowTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_stage", length = 20)
    private LeadStage triggerStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "cond_field", length = 20)
    private ConditionField condField;

    @Enumerated(EnumType.STRING)
    @Column(name = "cond_op", length = 10)
    private ConditionOp condOp;

    @Column(name = "cond_value", length = 100)
    private String condValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private WorkflowAction action;

    @Column(name = "action_text", length = 500)
    private String actionText;

    @Column(name = "action_days")
    private Integer actionDays;

    @Column(name = "action_user")
    private UUID actionUser;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Workflow create(TenantId tenantId, String name, WorkflowTrigger trigger, LeadStage triggerStage, ConditionField f, ConditionOp o, String v,
                                  WorkflowAction action, String text, Integer days, UUID user, boolean active) {
        var w = new Workflow();
        w.tenantId = tenantId;
        w.createdAt = Instant.now();
        w.change(name, trigger, triggerStage, f, o, v, action, text, days, user, active);
        return w;
    }

    public void change(String name, WorkflowTrigger trigger, LeadStage triggerStage, ConditionField f, ConditionOp o, String v,
                       WorkflowAction action, String text, Integer days, UUID user, boolean active) {
        this.name = name.strip();
        this.trigger = trigger;
        this.triggerStage = trigger == WorkflowTrigger.STAGE_CHANGED ? triggerStage : null;
        this.condField = f;
        this.condOp = f == null ? null : o;
        this.condValue = f == null ? null : v.strip();
        this.action = action;
        this.actionText = action == WorkflowAction.ASSIGN_OWNER || text == null ? null : text.strip();
        this.actionDays = action == WorkflowAction.CREATE_TASK ? days : null;
        this.actionUser = action == WorkflowAction.ASSIGN_OWNER || action == WorkflowAction.CREATE_TASK ? user : null;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void softDelete() { this.deletedAt = Instant.now(); this.updatedAt = this.deletedAt; }
}

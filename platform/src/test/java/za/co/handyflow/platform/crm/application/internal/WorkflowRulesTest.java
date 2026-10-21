package za.co.handyflow.platform.crm.application.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.LeadConditions.Condition;
import za.co.handyflow.platform.crm.application.internal.LeadConditions.Facts;
import za.co.handyflow.platform.crm.application.internal.WorkflowRules.Workflow;
import za.co.handyflow.platform.crm.domain.model.*;

class WorkflowRulesTest {
    private static final Facts F = new Facts(LeadSource.WEBSITE, LeadStage.QUALIFIED, new BigDecimal("60000"), true, true, true, Set.of());

    private static Workflow task(WorkflowTrigger t, LeadStage s) { return new Workflow(t, s, null, WorkflowAction.CREATE_TASK, "Send proposal", 2, null); }
    private static Workflow of(WorkflowAction a, String text, Integer days, UUID user) { return new Workflow(WorkflowTrigger.LEAD_CREATED, null, null, a, text, days, user); }
    private static void refuses(String name, Workflow w, String msg) {
        assertThatThrownBy(() -> WorkflowRules.check(name, w)).isInstanceOf(IllegalArgumentException.class).hasMessage(msg);
    }

    @Test
    void goodWorkflowsPass() {
        WorkflowRules.check("Proposal", task(WorkflowTrigger.STAGE_CHANGED, LeadStage.QUALIFIED));
        WorkflowRules.check("x", new Workflow(WorkflowTrigger.OPPORTUNITY_WON, null, null, WorkflowAction.ASSIGN_OWNER, null, null, UUID.randomUUID()));
    }

    @Test
    void badWorkflowsAreRefusedWithAReadableReason() {
        refuses(" ", task(WorkflowTrigger.LEAD_CREATED, null), "Name the workflow");
        refuses("x", new Workflow(null, null, null, WorkflowAction.ADD_TAG, "a", null, null), "Choose what starts the workflow");
        refuses("x", task(WorkflowTrigger.LEAD_CREATED, LeadStage.NEW), "A stage only applies to \"stage changed\"");
        refuses("x", of(null, "a", null, null), "Choose what the workflow does");
        refuses("x", of(WorkflowAction.CREATE_TASK, " ", 1, null), "Say what the task is");
        refuses("x", of(WorkflowAction.CREATE_TASK, "t", null, null), "Due in days must be between 0 and 365");
        refuses("x", of(WorkflowAction.CREATE_TASK, "t", 366, null), "Due in days must be between 0 and 365");
        refuses("x", of(WorkflowAction.ADD_TAG, "", null, null), "Name the tag");
        refuses("x", of(WorkflowAction.ADD_TAG, "x".repeat(51), null, null), "The tag can be at most 50 characters");
        refuses("x", of(WorkflowAction.ASSIGN_OWNER, null, null, null), "Choose who becomes the owner");
        refuses("x", of(WorkflowAction.NOTIFY_OWNER, "", null, null), "Write the message");
        refuses("x", new Workflow(WorkflowTrigger.LEAD_CREATED, null, new Condition(ConditionField.SOURCE, ConditionOp.EQ, "RADIO"), WorkflowAction.ADD_TAG, "a", null, null), "Choose a lead source");
    }

    @Test
    void appliesOnlyWhenTheEventStageAndConditionFit() {
        assertThat(WorkflowRules.applies(task(WorkflowTrigger.STAGE_CHANGED, LeadStage.QUALIFIED), WorkflowTrigger.STAGE_CHANGED, LeadStage.QUALIFIED, F)).isTrue();
        assertThat(WorkflowRules.applies(task(WorkflowTrigger.STAGE_CHANGED, LeadStage.WON), WorkflowTrigger.STAGE_CHANGED, LeadStage.QUALIFIED, F)).isFalse();
        assertThat(WorkflowRules.applies(task(WorkflowTrigger.STAGE_CHANGED, null), WorkflowTrigger.STAGE_CHANGED, LeadStage.LOST, F)).isTrue();
        assertThat(WorkflowRules.applies(task(WorkflowTrigger.LEAD_CREATED, null), WorkflowTrigger.STAGE_CHANGED, LeadStage.LOST, F)).isFalse();
        var big = new Workflow(WorkflowTrigger.LEAD_CREATED, null, new Condition(ConditionField.DEAL_VALUE, ConditionOp.GTE, "100000"), WorkflowAction.ADD_TAG, "big", null, null);
        assertThat(WorkflowRules.applies(big, WorkflowTrigger.LEAD_CREATED, null, F)).isFalse();
    }

    @Test
    void dueDateAndDescriptions() {
        assertThat(WorkflowRules.dueDate(LocalDate.of(2026, 10, 30), 3)).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(WorkflowRules.describe(task(WorkflowTrigger.STAGE_CHANGED, LeadStage.QUALIFIED), null))
                .isEqualTo("When a lead moves to qualified, add a task \"Send proposal\" due in 2 days");
        assertThat(WorkflowRules.describe(new Workflow(WorkflowTrigger.LEAD_CREATED, null, new Condition(ConditionField.SOURCE, ConditionOp.EQ, "WEBSITE"), WorkflowAction.ADD_TAG, " VIP ", null, null), null))
                .isEqualTo("When a lead is added and source is website, tag it \"vip\"");
        assertThat(WorkflowRules.describe(new Workflow(WorkflowTrigger.OPPORTUNITY_WON, null, null, WorkflowAction.ASSIGN_OWNER, null, null, UUID.randomUUID()), "Sam"))
                .isEqualTo("When an opportunity is won, make Sam the owner");
        assertThat(WorkflowRules.describe(new Workflow(WorkflowTrigger.STAGE_CHANGED, null, null, WorkflowAction.NOTIFY_OWNER, "Call them", null, null), null))
                .isEqualTo("When a lead changes stage, tell the owner: Call them");
        assertThat(WorkflowRules.describe(of(WorkflowAction.CREATE_TASK, "Call", 0, null), null)).endsWith("due the same day");
    }
}

package za.co.handyflow.platform.crm.application.internal;

import java.time.LocalDate;
import java.util.UUID;
import za.co.handyflow.platform.crm.application.internal.LeadConditions.Condition;
import za.co.handyflow.platform.crm.application.internal.LeadConditions.Facts;
import za.co.handyflow.platform.crm.domain.model.LeadStage;
import za.co.handyflow.platform.crm.domain.model.WorkflowAction;
import za.co.handyflow.platform.crm.domain.model.WorkflowTrigger;

/** What a workflow is, when it applies, and how it reads in plain language. Pure. */
final class WorkflowRules {
    static final int MAX_WORKFLOWS = 30, MAX_PER_EVENT = 10;

    private WorkflowRules() {}

    record Workflow(WorkflowTrigger trigger, LeadStage triggerStage, Condition condition, WorkflowAction action, String text, Integer days, UUID user) {}

    static void check(String name, Workflow w) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name the workflow");
        if (name.strip().length() > 120) throw new IllegalArgumentException("The name can be at most 120 characters");
        if (w.trigger() == null) throw new IllegalArgumentException("Choose what starts the workflow");
        if (w.triggerStage() != null && w.trigger() != WorkflowTrigger.STAGE_CHANGED) throw new IllegalArgumentException("A stage only applies to \"stage changed\"");
        if (w.condition() != null) LeadConditions.check(w.condition());
        if (w.action() == null) throw new IllegalArgumentException("Choose what the workflow does");
        String t = w.text() == null ? "" : w.text().strip();
        switch (w.action()) {
            case CREATE_TASK -> {
                if (t.isEmpty()) throw new IllegalArgumentException("Say what the task is");
                if (t.length() > 500) throw new IllegalArgumentException("The task can be at most 500 characters");
                if (w.days() == null || w.days() < 0 || w.days() > 365) throw new IllegalArgumentException("Due in days must be between 0 and 365");
            }
            case ADD_TAG -> {
                if (t.isEmpty()) throw new IllegalArgumentException("Name the tag");
                if (t.length() > 50) throw new IllegalArgumentException("The tag can be at most 50 characters");
            }
            case ASSIGN_OWNER -> { if (w.user() == null) throw new IllegalArgumentException("Choose who becomes the owner"); }
            case NOTIFY_OWNER -> {
                if (t.isEmpty()) throw new IllegalArgumentException("Write the message");
                if (t.length() > 500) throw new IllegalArgumentException("The message can be at most 500 characters");
            }
        }
    }

    /** True when the event starts this workflow: same trigger, the stage fits, and the lead meets the extra condition. */
    static boolean applies(Workflow w, WorkflowTrigger event, LeadStage stageMovedTo, Facts facts) {
        if (w.trigger() != event) return false;
        if (w.trigger() == WorkflowTrigger.STAGE_CHANGED && w.triggerStage() != null && w.triggerStage() != stageMovedTo) return false;
        return LeadConditions.matches(w.condition(), facts);
    }

    static LocalDate dueDate(LocalDate today, int days) { return today.plusDays(days); }

    static String describe(Workflow w, String ownerName) {
        String when = switch (w.trigger()) {
            case LEAD_CREATED -> "a lead is added";
            case STAGE_CHANGED -> w.triggerStage() == null ? "a lead changes stage" : "a lead moves to " + w.triggerStage().name().toLowerCase();
            case OPPORTUNITY_WON -> "an opportunity is won";
        };
        String only = w.condition() == null ? "" : " and " + LeadConditions.describe(w.condition()).toLowerCase();
        String does = switch (w.action()) {
            case CREATE_TASK -> "add a task \"" + w.text().strip() + "\" due " + (w.days() == 0 ? "the same day" : "in " + w.days() + (w.days() == 1 ? " day" : " days"));
            case ADD_TAG -> "tag it \"" + w.text().strip().toLowerCase() + "\"";
            case ASSIGN_OWNER -> "make " + (ownerName == null ? "a colleague" : ownerName) + " the owner";
            case NOTIFY_OWNER -> "tell the owner: " + w.text().strip();
        };
        return "When " + when + only + ", " + does;
    }
}

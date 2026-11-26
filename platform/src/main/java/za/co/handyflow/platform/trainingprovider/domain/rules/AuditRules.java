package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Internal quality audits. The provider writes its own checklists and judges each item itself; the system seeds no regulatory content and never
 * calls anyone compliant or competent because an item was answered. Completing an audit and verifying an action each need a second person, or a
 * written reason. Nothing is deleted.
 */
public final class AuditRules {

    public static final Set<String> SCOPES = Set.of("GENERAL", "SITE", "COHORT", "PROGRAMME");
    public static final String GENERAL = "GENERAL";
    public static final Set<String> OUTCOMES = Set.of("NO_ISSUE", "ISSUE", "NOTE", "NOT_APPLICABLE");
    public static final String NO_ISSUE = "NO_ISSUE";
    public static final String ISSUE = "ISSUE";
    public static final String PLANNED = "PLANNED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String OPEN = "OPEN";
    public static final String DONE = "DONE";
    public static final int MIN_NOTE = 10;
    public static final int MAX_ITEMS = 200;

    private AuditRules() {
    }

    /** The checklist items: one per non-blank line, trimmed. */
    public static List<String> parseItems(String itemsText) {
        List<String> out = new ArrayList<>();
        if (itemsText == null) {
            return out;
        }
        for (String line : itemsText.split("\\R")) {
            String t = line.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    public static String templateProblem(String name, String scope, String itemsText) {
        if (name == null || name.trim().length() < 2) {
            return "Give the checklist a name";
        }
        if (scope == null || !SCOPES.contains(scope)) {
            return "Choose what the checklist is for";
        }
        return itemsProblem(itemsText);
    }

    public static String itemsProblem(String itemsText) {
        List<String> items = parseItems(itemsText);
        if (items.isEmpty()) {
            return "Add at least one item, one per line";
        }
        if (items.size() > MAX_ITEMS) {
            return "A checklist can have at most " + MAX_ITEMS + " items";
        }
        for (String i : items) {
            if (i.length() < 2) {
                return "Each item needs at least two characters";
            }
        }
        if (items.stream().map(String::toLowerCase).distinct().count() != items.size()) {
            return "Two items are the same";
        }
        return null;
    }

    /** Why an audit cannot be set up against this checklist and scope, or null. */
    public static String setupProblem(String title, String templateScope, String auditScope, boolean hasTarget, LocalDate plannedOn) {
        if (title == null || title.trim().length() < 2) {
            return "Give the audit a title";
        }
        if (auditScope == null || !SCOPES.contains(auditScope)) {
            return "Choose what is being audited";
        }
        if (!GENERAL.equals(auditScope) && !hasTarget) {
            return "Choose the " + auditScope.toLowerCase() + " being audited";
        }
        if (GENERAL.equals(auditScope) && hasTarget) {
            return "A general audit is not about one site, cohort or programme";
        }
        if (!GENERAL.equals(templateScope) && !templateScope.equals(auditScope)) {
            return "That checklist is for a " + templateScope.toLowerCase() + " audit";
        }
        if (plannedOn == null) {
            return "Give the planned date";
        }
        return null;
    }

    /** An outcome of an item. Every outcome except "no issue seen" needs the auditor's words. */
    public static String answerProblem(String outcome, String note) {
        if (outcome == null || !OUTCOMES.contains(outcome)) {
            return "Choose an outcome";
        }
        if (!NO_ISSUE.equals(outcome) && (note == null || note.trim().length() < MIN_NOTE)) {
            return "Say what you saw, in at least " + MIN_NOTE + " characters";
        }
        return null;
    }

    /**
     * Why an audit cannot be completed, or null. Every item is answered, every issue has an action that is not cancelled, and a summary is written.
     */
    public static String completeProblem(int unanswered, int issuesWithoutAction, String summary) {
        if (unanswered > 0) {
            return unanswered + " item(s) are not answered yet";
        }
        if (issuesWithoutAction > 0) {
            return issuesWithoutAction + " issue(s) have no corrective action yet";
        }
        if (summary == null || summary.trim().length() < MIN_NOTE) {
            return "Write a summary of at least " + MIN_NOTE + " characters";
        }
        return null;
    }

    /** A second person completes or verifies, or the same person says why in writing. Null when acceptable. */
    public static String secondPersonProblem(UUID actedBefore, UUID actingNow, boolean confirmAlone, String aloneReason, String what) {
        if (actedBefore != null && actedBefore.equals(actingNow)) {
            if (!confirmAlone) {
                return "A second person should " + what + ", or confirm in writing that you are doing it yourself";
            }
            if (aloneReason == null || aloneReason.trim().length() < MIN_NOTE) {
                return "Say why you are doing this yourself, in at least " + MIN_NOTE + " characters";
            }
        }
        return null;
    }

    public static String actionProblem(String description, String owner, LocalDate dueOn, LocalDate today) {
        if (description == null || description.trim().length() < 5) {
            return "Say what has to be done";
        }
        if (owner == null || owner.trim().length() < 2) {
            return "Say who owns the action";
        }
        if (dueOn == null) {
            return "Give the day it is due";
        }
        if (dueOn.isBefore(today)) {
            return "The due date cannot be in the past";
        }
        return null;
    }

    public static String noteProblem(String note) {
        return note != null && note.trim().length() >= MIN_NOTE ? null : "Give a reason or note of at least " + MIN_NOTE + " characters";
    }

    /** OPEN, DUE_SOON-free: an open action past its due date reads as overdue. A prompt, not a finding. */
    public static boolean overdue(String status, LocalDate dueOn, LocalDate today) {
        return OPEN.equals(status) && dueOn != null && dueOn.isBefore(today);
    }
}

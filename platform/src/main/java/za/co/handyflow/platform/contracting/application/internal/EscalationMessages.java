package za.co.handyflow.platform.contracting.application.internal;

import za.co.handyflow.platform.contracting.domain.model.ApprovalAction;
import za.co.handyflow.platform.contracting.domain.model.EscalationRules.Stage;

/**
 * The words of the approval follow-up emails. Plain text and simple HTML only; everything that comes from a user (a contract title, a person's
 * name, a note) is escaped. Kept free of Spring so the wording can be tested directly.
 */
final class EscalationMessages {

    private EscalationMessages() {}

    record Mail(String subject, String html) {}

    static String actionLabel(ApprovalAction a) {
        return switch (a) {
            case SEND_FOR_SIGNING -> "Send for signing";
            case TERMINATE -> "Terminate contract";
            case PUBLISH_CLAUSE -> "Publish clause";
            case PUBLISH_TEMPLATE -> "Publish template";
            case ACCEPT_COUNTERPARTY_REDLINE -> "Accept a counterparty change";
            case SIGN_ON_BEHALF -> "Sign on behalf of a party";
            case WITHDRAW -> "Withdraw a contract sent for signing";
        };
    }

    /** "45 min", "2 h", "25 h 30 min". */
    static String duration(long minutes) {
        long m = Math.max(0, minutes);
        long h = m / 60, rest = m % 60;
        if (h == 0) return rest + " min";
        return rest == 0 ? h + " h" : h + " h " + rest + " min";
    }

    static String escape(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    private static String waitedText(long minutes, boolean workingClock) {
        return duration(minutes) + (workingClock ? " of working time" : "");
    }

    private static String facts(ApprovalAction action, String subject, String requestedBy, String requestedWhen, long waited, boolean workingClock) {
        return "<p><strong>" + escape(actionLabel(action)) + "</strong><br/>" + escape(subject) + "</p>"
                + "<p>Asked by " + escape(requestedBy) + " on " + escape(requestedWhen) + ". It has been waiting " + escape(waitedText(waited, workingClock)) + ".</p>";
    }

    private static String button(String link) {
        return "<p><a href=\"" + escape(link) + "\">Open approvals</a></p>";
    }

    /** A reminder to the people who can approve. {@code redirected} is set when the usual approvers are all away. */
    static Mail reminder(Stage stage, ApprovalAction action, String subject, String requestedBy, String requestedWhen, long waited,
                         boolean workingClock, String link, boolean redirected) {
        if (stage != Stage.REMINDER_1 && stage != Stage.REMINDER_2) throw new IllegalArgumentException("Not a reminder: " + stage);
        String prefix = stage == Stage.REMINDER_1 ? "Reminder" : "Second reminder";
        String lead = redirected
                ? "<p>This approval is waiting and the people who usually approve it are away, so it has come to you.</p>"
                : "<p>An approval is waiting for a decision and has not had one yet.</p>";
        return new Mail(prefix + ": approval waiting. " + actionLabel(action) + ", " + subject,
                lead + facts(action, subject, requestedBy, requestedWhen, waited, workingClock)
                        + "<p>Please approve or reject it, or ask a colleague who can.</p>" + button(link));
    }

    /** Sent to the escalation contacts when a request has waited too long. {@code reminded} names who was already reminded (may be empty). */
    static Mail escalation(ApprovalAction action, String subject, String requestedBy, String requestedWhen, long waited,
                           boolean workingClock, String link, String reminded, boolean stepsSkipped) {
        String who = reminded == null || reminded.isBlank() ? "" : "<p>Already reminded: " + escape(reminded) + ".</p>";
        String skipped = stepsSkipped ? "<p>No earlier reminder was sent for this one.</p>" : "";
        return new Mail("Escalated: approval waiting too long. " + actionLabel(action) + ", " + subject,
                "<p>This approval has waited longer than your organisation allows and needs attention. Nothing has been approved or rejected on anyone's behalf.</p>"
                        + facts(action, subject, requestedBy, requestedWhen, waited, workingClock) + who + skipped
                        + "<p>You can decide it, or reassign it to someone who can.</p>" + button(link));
    }

    /** Sent to the person who asked, so they know it is being chased. */
    static Mail toRequester(ApprovalAction action, String subject, long waited, boolean workingClock, String link, boolean someoneTold) {
        String line = someoneTold
                ? "<p>It has been passed to the people your organisation chose for this, so it gets looked at.</p>"
                : "<p>There was no one available to pass it to. You may want to ask someone who can approve it.</p>";
        return new Mail("Your approval request has been escalated: " + actionLabel(action) + ", " + subject,
                "<p>Your request has been waiting " + escape(waitedText(waited, workingClock)) + " without a decision.</p>"
                        + "<p><strong>" + escape(actionLabel(action)) + "</strong><br/>" + escape(subject) + "</p>" + line + button(link));
    }

    /** Sent to the person an approval has just been assigned to. */
    static Mail assigned(ApprovalAction action, String subject, String assignedBy, String requestedBy, String requestedWhen, long waited,
                         boolean workingClock, String link, String note) {
        String n = note == null || note.isBlank() ? "" : "<p>Note: " + escape(note) + "</p>";
        return new Mail("Approval assigned to you: " + actionLabel(action) + ", " + subject,
                "<p>" + escape(assignedBy) + " has asked you to look at this approval.</p>"
                        + facts(action, subject, requestedBy, requestedWhen, waited, workingClock) + n + button(link));
    }
}

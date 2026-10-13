package za.co.handyflow.platform.clinic.application.internal;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Wording and recipients of the alert sent when someone breaks the glass on a restricted record (patch 0189, CLINIC-DEC-009).
 * The alert never names the patient and never carries the reason: email is not a safe place for either. It says who, when and
 * where to look.
 */
final class BreakGlassAlertRules {
    private BreakGlassAlertRules() {}

    record Reviewer(UUID userId, String email, String name) {}

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.of("Africa/Johannesburg"));

    /** People to email: one entry per address, no blank addresses, and not the person who broke the glass. */
    static List<Reviewer> recipients(Collection<Reviewer> reviewers, UUID actor) {
        Map<String, Reviewer> byEmail = new LinkedHashMap<>();
        for (Reviewer r : reviewers) {
            if (r == null || r.email() == null || r.email().isBlank()) continue;
            if (actor != null && actor.equals(r.userId())) continue;
            byEmail.putIfAbsent(r.email().trim().toLowerCase(), r);
        }
        return new ArrayList<>(byEmail.values());
    }

    static String who(String actorName) { return actorName == null || actorName.isBlank() ? "A staff member" : actorName.trim(); }

    static String subject() { return "Restricted record opened: please review"; }

    static String emailBody(String reviewerName, String actorName, Instant at, Instant until) {
        String hello = reviewerName == null || reviewerName.isBlank() ? "Hello" : "Hello " + reviewerName.trim();
        return "<p>" + hello + ",</p>"
                + "<p>" + esc(who(actorName)) + " opened a restricted patient record at " + WHEN.format(at) + " using break-glass access"
                + " (open until " + WHEN.format(until) + ").</p>"
                + "<p>Please open <b>Break-glass review</b> in the clinic to see the reason given and what was viewed, and acknowledge it once checked.</p>"
                + "<p>This message does not name the patient or give the reason, on purpose.</p>";
    }

    static String taskTitle(String actorName) { return "Review break-glass access by " + who(actorName); }

    static String taskDetail(Instant at) { return "A restricted record was opened at " + WHEN.format(at) + ". Open Break-glass review to see the reason and what was viewed."; }

    private static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}

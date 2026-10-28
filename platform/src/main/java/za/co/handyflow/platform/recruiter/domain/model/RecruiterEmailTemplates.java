package za.co.handyflow.platform.recruiter.domain.model;

import java.util.List;

/**
 * The emails candidates receive. Pure. Every value that came from a person (a name, a job title, a venue) is escaped before it goes into the HTML,
 * and the internal reason for a rejection is never put in the email.
 */
public final class RecruiterEmailTemplates {
    private RecruiterEmailTemplates() {}

    public record Mail(String subject, String body) {}

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String greeting(String firstName) { return "<h2>Hi " + esc(firstName) + ",</h2>"; }

    private static String portal(String portalUrl) { return "<p><a href=\"" + esc(portalUrl) + "\">View your application</a></p>"; }

    public static Mail confirmation(String firstName, String jobTitle, String portalUrl) {
        return new Mail("Application received — " + oneLine(jobTitle),
                greeting(firstName) + "<p>We've received your application for <strong>" + esc(jobTitle) + "</strong>.</p>"
                        + "<p>You can track your application status at any time using the link below:</p>"
                        + "<p><a href=\"" + esc(portalUrl) + "\" style=\"background:#1B3A6B;color:white;padding:12px 24px;border-radius:8px;"
                        + "text-decoration:none;display:inline-block\">Track My Application</a></p>"
                        + "<p style=\"color:#64748B;font-size:12px\">Bookmark this link — it's unique to you and doesn't require a password.</p>");
    }

    public static Mail stageChange(String firstName, String jobTitle, String stageLabel, String portalUrl) {
        return new Mail("Application update — " + oneLine(jobTitle),
                greeting(firstName) + "<p>Your application for <strong>" + esc(jobTitle) + "</strong> has been updated.</p>"
                        + "<p>Status: <strong>" + esc(stageLabel) + "</strong></p>" + portal(portalUrl));
    }

    /** No reason: the reason recorded against a rejection is for staff. */
    public static Mail rejected(String firstName, String jobTitle) {
        return new Mail("Application outcome — " + oneLine(jobTitle),
                greeting(firstName) + "<p>Thank you for your interest in <strong>" + esc(jobTitle) + "</strong>.</p>"
                        + "<p>After careful consideration, we will not be moving forward with your application at this time.</p>"
                        + "<p>We appreciate the time you invested and wish you the best in your search.</p>");
    }

    public static Mail interviewScheduled(String firstName, String jobTitle, String type, String when, String interviewer, List<String> panel,
                                          String location, boolean video, String portalUrl) {
        return new Mail("Interview scheduled — " + oneLine(jobTitle),
                greeting(firstName) + "<p>An interview has been scheduled for your application to <strong>" + esc(jobTitle) + "</strong>.</p>"
                        + "<p>Type: <strong>" + esc(type) + "</strong><br>When: <strong>" + esc(when) + "</strong>"
                        + (blank(interviewer) ? "" : "<br>Interviewer: <strong>" + esc(interviewer) + "</strong>")
                        + (panel == null || panel.isEmpty() ? "" : "<br>Also interviewing: <strong>" + esc(String.join(", ", panel)) + "</strong>")
                        + place(location, video) + "</p>" + portal(portalUrl));
    }

    public static Mail interviewRescheduled(String firstName, String jobTitle, String reason, String when, String interviewer, String location,
                                            boolean video, String portalUrl) {
        return new Mail("Interview rescheduled — " + oneLine(jobTitle),
                greeting(firstName) + "<p>Your interview for <strong>" + esc(jobTitle) + "</strong> has been rescheduled.</p>"
                        + (blank(reason) ? "" : "<p>Reason: " + esc(reason) + "</p>")
                        + "<p>New time: <strong>" + esc(when) + "</strong>"
                        + (blank(interviewer) ? "" : "<br>Interviewer: <strong>" + esc(interviewer) + "</strong>")
                        + place(location, video) + "</p>" + portal(portalUrl));
    }

    public static Mail offer(String firstName, String jobTitle, String openUntil) {
        return new Mail("Your offer — " + oneLine(jobTitle),
                greeting(firstName) + "<p>Please find your offer letter for <strong>" + esc(jobTitle) + "</strong> attached.</p>"
                        + "<p>Congratulations — we look forward to hearing from you. This offer is open until " + esc(openUntil) + ".</p>");
    }

    private static String place(String location, boolean video) {
        return blank(location) ? "" : "<br>" + (video ? "Meeting link" : "Venue") + ": <strong>" + esc(location) + "</strong>";
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    /** A subject must stay on one line, or a name with a line break could add headers. */
    static String oneLine(String s) { return s == null ? "" : s.replaceAll("[\\r\\n]+", " ").trim(); }
}

// security/application/internal/ReportEmailRules.java
package za.co.handyflow.platform.security.application.internal;

import za.co.handyflow.platform.shared.HandyFlowException;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** The rules for emailing the monthly service report to a client. Pure: no database, no mail. */
public final class ReportEmailRules {

    public static final int MAX_RECIPIENTS = 5;
    public static final Duration RESEND_AFTER = Duration.ofMinutes(10);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>,;\"']+@[^\\s@<>,;\"'.]+(\\.[^\\s@<>,;\"'.]+)+$");

    /** A line of the report: a heading and its sentence(s). */
    public record Line(String heading, String text) {}
    public record Content(String siteName, String monthTitle, String period, List<Line> lines) {}

    private ReportEmailRules() {}

    /** Lower-cased and trimmed, or a 400. No spaces, commas, angle brackets or quotes can get through, so one box is one address. */
    public static String cleanEmail(String raw) {
        String e = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (e.length() > 254 || !EMAIL.matcher(e).matches())
            throw new HandyFlowException("Enter one valid email address", HttpStatus.BAD_REQUEST, "INVALID_EMAIL");
        return e;
    }

    public static String cleanName(String raw) {
        String n = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}]", " ").trim().replaceAll("\\s+", " ");
        if (n.isEmpty() || n.length() > 100) throw new HandyFlowException("Name must be 1 to 100 characters", HttpStatus.BAD_REQUEST, "INVALID_NAME");
        return n;
    }

    public static void requireRoom(int activeRecipients) {
        if (activeRecipients >= MAX_RECIPIENTS)
            throw new HandyFlowException("A site can have at most " + MAX_RECIPIENTS + " report recipients", HttpStatus.CONFLICT, "TOO_MANY_RECIPIENTS");
    }

    /** Only a finished month goes to a client; a month to date is still changing. */
    public static void requireFinished(YearMonth month, Instant now) {
        if (!month.atEndOfMonth().isBefore(java.time.LocalDate.ofInstant(now, WeeklyHoursRules.SAST)))
            throw new HandyFlowException("Only a finished month can be emailed", HttpStatus.CONFLICT, "MONTH_NOT_FINISHED");
    }

    public static void requireRecipients(int n) {
        if (n == 0) throw new HandyFlowException("Add at least one recipient first", HttpStatus.CONFLICT, "NO_RECIPIENTS");
    }

    /** The same month of the same site is not sent again within ten minutes, so a double click does not email the client twice. */
    public static void requireNotJustSent(Instant lastSent, Instant now) {
        if (lastSent != null && lastSent.plus(RESEND_AFTER).isAfter(now))
            throw new HandyFlowException("This report was just sent. Try again in a few minutes.", HttpStatus.CONFLICT, "JUST_SENT");
    }

    public static String monthTitle(YearMonth m) {
        return m.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear();
    }

    public static String subject(String siteName, String monthTitle) {
        return "Monthly service report — " + siteName.replaceAll("[\\p{Cntrl}]", " ").trim() + " — " + monthTitle;
    }

    public static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static String html(Content c) {
        StringBuilder b = new StringBuilder();
        b.append("<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#111;max-width:640px\">");
        b.append("<h2 style=\"margin:0 0 4px\">Monthly service report</h2>");
        b.append("<p style=\"margin:0 0 16px;color:#444\">").append(esc(c.siteName())).append(" — ").append(esc(c.monthTitle()))
                .append("<br>Period ").append(esc(c.period())).append("</p>");
        b.append("<table style=\"border-collapse:collapse;width:100%\">");
        for (Line l : c.lines()) {
            b.append("<tr><td style=\"padding:6px 8px;border-bottom:1px solid #ddd;font-weight:bold;vertical-align:top;white-space:nowrap\">")
                    .append(esc(l.heading())).append("</td><td style=\"padding:6px 8px;border-bottom:1px solid #ddd\">")
                    .append(esc(l.text())).append("</td></tr>");
        }
        b.append("</table><p style=\"margin:16px 0 0;color:#666;font-size:12px\">Figures are counts only. Sent on behalf of your security provider through HandyFlow.</p></div>");
        return b.toString();
    }
}

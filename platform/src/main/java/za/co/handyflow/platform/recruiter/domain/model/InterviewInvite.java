package za.co.handyflow.platform.recruiter.domain.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** A calendar invite (.ics) for an interview, so it can be put in any calendar. Pure. */
public final class InterviewInvite {
    private InterviewInvite() {}

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    public static String problem(Instant start) {
        return start == null ? "This interview has no time yet, so there is nothing to put in a calendar." : null;
    }

    /** RFC 5545: escape \ ; , and newlines; fold lines longer than 75 octets; CRLF line ends. */
    public static String build(String uid, Instant start, int minutes, String summary, String description, String location,
                               String organiserName, Instant now) {
        StringBuilder b = new StringBuilder();
        line(b, "BEGIN:VCALENDAR");
        line(b, "VERSION:2.0");
        line(b, "PRODID:-//HandyFlow//Recruiter//EN");
        line(b, "CALSCALE:GREGORIAN");
        line(b, "METHOD:PUBLISH");
        line(b, "BEGIN:VEVENT");
        line(b, "UID:" + uid + "@handyflow");
        line(b, "DTSTAMP:" + STAMP.format(now));
        line(b, "DTSTART:" + STAMP.format(start));
        line(b, "DTEND:" + STAMP.format(start.plusSeconds(60L * minutes)));
        line(b, "SUMMARY:" + escape(summary));
        if (description != null && !description.isBlank()) line(b, "DESCRIPTION:" + escape(description));
        if (location != null && !location.isBlank()) line(b, "LOCATION:" + escape(location));
        if (organiserName != null && !organiserName.isBlank()) line(b, "ORGANIZER;CN=" + escape(organiserName).replace(":", "") + ":mailto:noreply@handyflow.local");
        line(b, "END:VEVENT");
        line(b, "END:VCALENDAR");
        return b.toString();
    }

    static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    private static void line(StringBuilder out, String text) {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= 75) { out.append(text).append("\r\n"); return; }
        int i = 0;
        boolean first = true;
        while (i < text.length()) {
            int limit = first ? 75 : 74;
            int used = 0, j = i;
            while (j < text.length()) {
                int cp = text.codePointAt(j);
                int len = new String(Character.toChars(cp)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (used + len > limit) break;
                used += len; j += Character.charCount(cp);
            }
            out.append(first ? "" : " ").append(text, i, j).append("\r\n");
            i = j; first = false;
        }
    }
}

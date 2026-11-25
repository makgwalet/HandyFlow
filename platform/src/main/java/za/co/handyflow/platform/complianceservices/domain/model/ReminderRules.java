package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What goes into the daily digest. Pure, so the choices can be tested. The digest repeats what is already in the work queue; it never
 * creates work, and a "handed over" record is not proof anyone read it.
 */
public final class ReminderRules {

    private ReminderRules() {}

    public static final Set<String> SEVERITIES = Set.of("HIGH", "MEDIUM", "LOW");
    public static final int MIN_ITEMS = 5;
    public static final int MAX_ITEMS = 100;
    public static final int MAX_RECIPIENTS = 25;

    /** One line of the queue, as the digest needs it. */
    public record Line(String severity, String clientName, String title, String detail) {}

    /** What was chosen, and how many more matched but did not fit. */
    public record Selection(List<Line> lines, int total, int urgent) {
        public int more() { return total - lines.size(); }
    }

    public static String severity(String raw) {
        String s = raw == null ? "" : raw.trim().toUpperCase();
        if (!SEVERITIES.contains(s)) throw new IllegalArgumentException("Choose how urgent an item must be to be included: urgent, soon or all");
        return s;
    }

    public static int maxItems(int n) {
        if (n < MIN_ITEMS || n > MAX_ITEMS) throw new IllegalArgumentException("A digest lists between " + MIN_ITEMS + " and " + MAX_ITEMS + " items");
        return n;
    }

    private static int rank(String severity) { return "HIGH".equals(severity) ? 0 : "MEDIUM".equals(severity) ? 1 : 2; }

    /** Items at or above the chosen urgency, in the order the queue gave them, cut to the digest's length. */
    public static Selection select(List<Line> queue, String minSeverity, int maxItems) {
        int floor = rank(minSeverity);
        List<Line> matching = new ArrayList<>();
        for (Line l : queue) if (rank(l.severity()) <= floor) matching.add(l);
        int urgent = 0;
        for (Line l : matching) if ("HIGH".equals(l.severity())) urgent++;
        return new Selection(List.copyOf(matching.subList(0, Math.min(matching.size(), maxItems))), matching.size(), urgent);
    }

    /** The digest goes out on working days. A person can still ask for one at any time. */
    public static boolean isSendDay(LocalDate day) {
        DayOfWeek d = day.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
    }

    public static String subject(Selection s) {
        String items = s.total() + (s.total() == 1 ? " item needs" : " items need") + " attention";
        return "Compliance Services: " + items + (s.urgent() > 0 ? " (" + s.urgent() + " urgent)" : "");
    }

    private static String tag(String severity) { return "HIGH".equals(severity) ? "Urgent" : "MEDIUM".equals(severity) ? "Soon" : "When you can"; }

    public static String body(LocalDate today, Selection s) {
        StringBuilder b = new StringBuilder("Work queue for ").append(today).append(":\n");
        for (Line l : s.lines()) {
            b.append("- [").append(tag(l.severity())).append("] ");
            if (l.clientName() != null && !l.clientName().isBlank()) b.append(l.clientName()).append(" · ");
            b.append(l.title());
            if (l.detail() != null && !l.detail().isBlank()) b.append(" — ").append(l.detail());
            b.append('\n');
        }
        if (s.more() > 0) b.append("…and ").append(s.more()).append(" more in the work queue.\n");
        return b.toString().trim();
    }

    /** Never says "delivered": handing a message over is all that is known. */
    public static String outcomeText(String outcome) {
        return switch (outcome) {
            case "HANDED_OVER" -> "Handed to the notification service";
            case "NOTHING_DUE" -> "Nothing to send";
            case "NO_ADDRESS" -> "No contact details found for this person";
            default -> "Could not be handed over";
        };
    }
}

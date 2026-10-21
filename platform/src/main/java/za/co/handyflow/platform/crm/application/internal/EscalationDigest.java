package za.co.handyflow.platform.crm.application.internal;

import java.time.LocalDate;
import java.util.List;

/** Words for the manager alert about late tasks. Pure logic, no database. */
public final class EscalationDigest {

    /** Most tasks listed in one alert; the rest are counted. */
    public static final int MAX_LISTED = 10;

    /** One late task as the alert describes it. */
    public record Item(String customerName, String note, LocalDate dueDate) {}

    private EscalationDigest() {}

    public static String title(int count) {
        return count == 1 ? "1 task is badly overdue" : count + " tasks are badly overdue";
    }

    /** Oldest first as given. Names the customer, how late, and the note, for the first MAX_LISTED. */
    public static String message(List<Item> items, LocalDate today) {
        StringBuilder sb = new StringBuilder(items.size() == 1 ? "This follow-up has been overdue for days:" : "These follow-ups have been overdue for days:");
        int shown = Math.min(items.size(), MAX_LISTED);
        for (int i = 0; i < shown; i++) {
            Item it = items.get(i);
            long late = java.time.temporal.ChronoUnit.DAYS.between(it.dueDate(), today);
            sb.append("\n- ").append(it.customerName() == null || it.customerName().isBlank() ? "A customer" : it.customerName())
                    .append(" (").append(late).append(late == 1 ? " day late" : " days late").append(')');
            if (it.note() != null && !it.note().isBlank()) {
                String n = it.note().strip();
                sb.append(": ").append(n.length() > 80 ? n.substring(0, 77) + "..." : n);
            }
        }
        if (items.size() > shown) sb.append("\n...and ").append(items.size() - shown).append(" more.");
        sb.append("\nOpen the CRM task queue to reassign or follow up.");
        return sb.toString();
    }
}

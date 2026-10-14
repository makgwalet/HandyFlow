package za.co.handyflow.platform.accountant.application.internal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Pure rules for recurring services: when a run falls due and what a schedule must contain. */
public final class ServiceScheduleRules {
    private ServiceScheduleRules() {}

    public static final Set<String> TYPES = Set.of("BOOKKEEPING", "VAT_RETURNS", "PAYROLL", "ANNUAL_FINANCIAL_STATEMENTS", "TAX_RETURNS", "ADVISORY", "OTHER");
    public static final Set<String> FREQUENCIES = Set.of("MONTHLY", "QUARTERLY", "ANNUALLY");
    public static final int MAX_CATCH_UP = 12;
    /** A run appears this many days before its date, so it can be billed on the day. */
    public static final int LEAD_DAYS = 5;

    public static int months(String frequency) {
        return switch (frequency) { case "MONTHLY" -> 1; case "QUARTERLY" -> 3; case "ANNUALLY" -> 12; default -> throw new IllegalArgumentException(frequency); };
    }

    /** Run n is the first date plus n periods, anchored so 31 January, 28 February, 31 March do not drift. */
    public static LocalDate runDate(LocalDate first, String frequency, int index) {
        LocalDate d = first.plusMonths((long) months(frequency) * index);
        return d;
    }

    public static String problem(String type, String frequency, LocalDate first, LocalDate end, BigDecimal fee, BigDecimal budgetHours, int dueDays, String description) {
        if (!TYPES.contains(type)) return "Choose the service.";
        if (!FREQUENCIES.contains(frequency)) return "Choose how often.";
        if (first == null) return "Choose the first date.";
        if (end != null && end.isBefore(first)) return "The end date is before the first date.";
        if (fee != null && fee.signum() < 0) return "The fee cannot be negative.";
        if (budgetHours != null && (budgetHours.signum() < 0 || budgetHours.compareTo(new BigDecimal("1000")) > 0)) return "Budget hours must be between 0 and 1000.";
        if (dueDays < 0 || dueDays > 120) return "Payment terms must be 0 to 120 days.";
        if (description != null && description.length() > 300) return "The description is too long.";
        return null;
    }

    /** Run indexes after {@code lastIndex} (-1 when none yet) that are due on {@code today}, oldest first, at most MAX_CATCH_UP. */
    public static List<Integer> dueIndexes(LocalDate first, String frequency, LocalDate end, int lastIndex, LocalDate today) {
        List<Integer> out = new ArrayList<>();
        for (int i = lastIndex + 1; out.size() < MAX_CATCH_UP; i++) {
            LocalDate d = runDate(first, frequency, i);
            if (end != null && d.isAfter(end)) break;
            if (d.minusDays(LEAD_DAYS).isAfter(today)) break;
            out.add(i);
        }
        return out;
    }

    public static String billProblem(String runStatus, String scheduleStatus, BigDecimal fee) {
        if (!"DUE".equals(runStatus)) return "This run has already been dealt with.";
        if ("ENDED".equals(scheduleStatus)) return "The schedule has ended.";
        if (fee == null || fee.signum() <= 0) return "This schedule has no fee to bill.";
        return null;
    }

    public static String skipProblem(String runStatus, String note) {
        if (!"DUE".equals(runStatus)) return "This run has already been dealt with.";
        if (note == null || note.isBlank()) return "Say why it is skipped.";
        return null;
    }

    public static String statusProblem(String current, String target) {
        if ("ENDED".equals(current)) return "An ended schedule cannot be changed.";
        if ("PAUSED".equals(target) && !"ACTIVE".equals(current)) return "Only an active schedule can be paused.";
        if ("ACTIVE".equals(target) && !"PAUSED".equals(current)) return "Only a paused schedule can be resumed.";
        return null;
    }

    public static String runDescription(String type, String description, LocalDate runDate, String frequency) {
        String label = type.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
        String period = switch (frequency) {
            case "MONTHLY" -> runDate.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + runDate.getYear();
            case "QUARTERLY" -> "quarter from " + runDate;
            default -> "year from " + runDate;
        };
        return label + (description == null || description.isBlank() ? "" : " (" + description.trim() + ")") + ", " + period;
    }
}

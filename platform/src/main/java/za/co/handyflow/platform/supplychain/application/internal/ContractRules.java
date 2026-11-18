package za.co.handyflow.platform.supplychain.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/** Pure rules for supplier contracts. */
public final class ContractRules {

    private ContractRules() {}

    public record Term(LocalDate start, LocalDate end) {}

    /** DRAFT -> ACTIVE | TERMINATED; ACTIVE -> EXPIRED | TERMINATED. EXPIRED and TERMINATED are final. */
    public static boolean canMove(String from, String to) {
        return switch (from) {
            case "DRAFT" -> Set.of("ACTIVE", "TERMINATED").contains(to);
            case "ACTIVE" -> Set.of("EXPIRED", "TERMINATED").contains(to);
            default -> false;
        };
    }

    public static String problem(LocalDate start, LocalDate end, int renewalNoticeDays, BigDecimal valueCap) {
        if (start == null || end == null) return "A start and an end date are required";
        if (end.isBefore(start)) return "The end date cannot be before the start date";
        if (renewalNoticeDays < 0 || renewalNoticeDays > 365) return "The renewal notice must be between 0 and 365 days";
        if (valueCap != null && valueCap.signum() <= 0) return "A value cap, when given, must be above zero";
        return null;
    }

    /** True from {@code noticeDays} before the end date until the end date itself. */
    public static boolean renewalNoticeDue(LocalDate end, LocalDate today, int noticeDays) {
        return !today.isAfter(end) && !today.isBefore(end.minusDays(noticeDays));
    }

    public static boolean hasEnded(LocalDate end, LocalDate today) { return today.isAfter(end); }

    /** The next term for an auto-renewing contract: starts the day after the old one ended and runs for the same number of days. */
    public static Term nextTerm(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate nextStart = end.plusDays(1);
        return new Term(nextStart, nextStart.plusDays(days - 1));
    }

    /** Percentage the PO price is above the agreed price (to one decimal), or null when it is at or below it. */
    public static BigDecimal overAgreedPct(BigDecimal poUnitCost, BigDecimal agreedPrice) {
        if (poUnitCost == null || agreedPrice == null || agreedPrice.signum() <= 0 || poUnitCost.compareTo(agreedPrice) <= 0) return null;
        return poUnitCost.subtract(agreedPrice).multiply(BigDecimal.valueOf(100)).divide(agreedPrice, 1, RoundingMode.HALF_UP);
    }
}

package za.co.handyflow.platform.complianceservices.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Rules for agreements, with no database in them. An agreement turns an accepted quote into an engagement: a term,
 * a billing arrangement and the scope as it was quoted. The schedule here is a preview of when the client would be
 * billed; nothing is invoiced from it yet.
 */
public final class AgreementRules {

    private AgreementRules() {}

    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String SUSPENDED = "SUSPENDED";
    public static final String ENDED = "ENDED";
    public static final String CANCELLED = "CANCELLED";
    public static final Set<String> STATUSES = Set.of(DRAFT, ACTIVE, SUSPENDED, ENDED, CANCELLED);

    public static final String ONCE = "ONCE";
    public static final Set<String> FREQUENCIES = Set.of(ONCE, "MONTHLY", "QUARTERLY", "ANNUALLY");

    public static final int MAX_TERM_YEARS = 10;
    /** An agreement with no end date shows this many billing dates, no more. */
    public static final int MAX_OPEN_DATES = 24;
    public static final int MAX_DATES = 120;
    public static final int MAX_NOTICE_DAYS = 365;

    public record Entry(LocalDate date, BigDecimal amount) {}

    public static int monthsPer(String frequency) {
        if (frequency == null) return 0;
        return switch (frequency) {
            case "MONTHLY" -> 1;
            case "QUARTERLY" -> 3;
            case "ANNUALLY" -> 12;
            default -> 0;
        };
    }

    /**
     * Billing dates from the first one, each worked out from the first date rather than the one before, so a bill on
     * the 31st falls on the last day of a short month and comes back to the 31st afterwards.
     */
    public static List<LocalDate> schedule(LocalDate first, LocalDate end, String frequency) {
        List<LocalDate> out = new ArrayList<>();
        if (first == null || frequency == null) return out;
        if (end != null && first.isAfter(end)) return out;
        if (ONCE.equals(frequency)) {
            out.add(first);
            return out;
        }
        int step = monthsPer(frequency);
        if (step == 0) return out;
        int cap = end == null ? MAX_OPEN_DATES : MAX_DATES;
        for (int i = 0; i < cap; i++) {
            LocalDate d = first.plusMonths((long) i * step);
            if (end != null && d.isAfter(end)) break;
            out.add(d);
        }
        return out;
    }

    public static List<Entry> entries(LocalDate first, LocalDate end, String frequency, BigDecimal amount) {
        List<Entry> out = new ArrayList<>();
        if (amount == null) return out;
        for (LocalDate d : schedule(first, end, frequency)) out.add(new Entry(d, amount));
        return out;
    }

    /** The whole contract, only when the term has an end. An open-ended agreement has no total to state. */
    public static BigDecimal contractValue(LocalDate first, LocalDate end, String frequency, BigDecimal amount) {
        if (amount == null || end == null) return null;
        int n = schedule(first, end, frequency).size();
        return amount.multiply(BigDecimal.valueOf(n)).setScale(2, RoundingMode.HALF_UP);
    }

    public static LocalDate nextBilling(LocalDate first, LocalDate end, String frequency, LocalDate today) {
        for (LocalDate d : schedule(first, end, frequency)) {
            if (!d.isBefore(today)) return d;
        }
        return null;
    }

    public static String termProblem(LocalDate start, LocalDate end) {
        if (start == null) return "A start date is required";
        if (end == null) return null;
        if (end.isBefore(start)) return "The end date cannot be before the start date";
        if (end.isAfter(start.plusYears(MAX_TERM_YEARS))) return "An agreement cannot run for more than " + MAX_TERM_YEARS + " years. Use a shorter term and renew it";
        return null;
    }

    public static String billingProblem(String frequency, BigDecimal amount, LocalDate first, LocalDate start, LocalDate end) {
        if (frequency == null || !FREQUENCIES.contains(frequency)) return "Billing frequency must be one of " + String.join(", ", new java.util.TreeSet<>(FREQUENCIES));
        if (amount == null || amount.signum() <= 0) return "The amount billed each time must be more than zero";
        if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) return "The amount cannot have more than two decimal places";
        if (amount.compareTo(new BigDecimal("1000000000")) >= 0) return "The amount is too large";
        if (first == null) return "A first billing date is required";
        if (start != null && first.isBefore(start)) return "The first billing date cannot be before the start date";
        if (end != null && first.isAfter(end)) return "The first billing date cannot be after the end date";
        if (!ONCE.equals(frequency) && end != null && schedule(first, end, frequency).isEmpty()) return "No billing date falls inside the term";
        return null;
    }

    public static String noticeProblem(int days) {
        if (days < 0 || days > MAX_NOTICE_DAYS) return "Notice must be between 0 and " + MAX_NOTICE_DAYS + " days";
        return null;
    }

    /** Information only: the contract can differ from the quote, for example when it is billed in instalments with a fee added. */
    public static String comparisonNote(BigDecimal contractValue, BigDecimal quotedProfessional) {
        if (contractValue == null || quotedProfessional == null) return null;
        int c = contractValue.compareTo(quotedProfessional);
        if (c == 0) return null;
        BigDecimal diff = contractValue.subtract(quotedProfessional).abs().setScale(2, RoundingMode.HALF_UP);
        return "Billed over the term this comes to " + (c > 0 ? diff + " more" : diff + " less") + " than the professional fees on the quote (before VAT). Check that this is what was agreed";
    }

    /** A line of text for whoever is looking at the agreement, or null when there is nothing to say. */
    public static String attention(String status, LocalDate end, boolean autoRenew, int noticeDays, LocalDate today) {
        if (!ACTIVE.equals(status) || end == null || today == null) return null;
        if (end.isBefore(today)) {
            return autoRenew
                    ? "The term ended on " + end + ". Renewal is not automatic yet, so end this agreement or start a new one"
                    : "The term ended on " + end + ". End this agreement or start a new one";
        }
        LocalDate noticeBy = end.minusDays(noticeDays);
        if (!today.isBefore(noticeBy)) {
            return autoRenew
                    ? "Notice to stop the renewal was due by " + noticeBy + ". The term ends on " + end
                    : "The term ends on " + end + ". Agree what happens next with the client";
        }
        return null;
    }

    public static boolean canEdit(String status) { return DRAFT.equals(status); }

    public static List<String> actionsFor(String status) {
        return switch (status == null ? "" : status) {
            case DRAFT -> List.of("activate", "cancel");
            case ACTIVE -> List.of("suspend", "end");
            case SUSPENDED -> List.of("resume", "end");
            default -> List.of();
        };
    }
}

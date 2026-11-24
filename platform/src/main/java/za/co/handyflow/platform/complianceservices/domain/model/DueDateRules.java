package za.co.handyflow.platform.complianceservices.domain.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Works out the dates an obligation falls due from its due rule and what is known about the client. Pure, so every rule
 * can be tested. A rule that needs something nobody has recorded is reported, never guessed.
 */
public final class DueDateRules {

    private DueDateRules() {}

    public static final int MAX_HORIZON_MONTHS = 24;

    /** What the client's record knows. Event and expiry dates are not held centrally yet. */
    public record Inputs(Integer financialYearEndMonth, LocalDate registeredOn) {}

    public record Rule(String type, Integer month, Integer day, Integer offsetDays, Integer offsetMonths) {}

    /** Dates within the window, or the reason there are none. */
    public record Result(List<LocalDate> dates, String reason) {
        static Result of(List<LocalDate> d) { return new Result(d, null); }
        static Result missing(String reason) { return new Result(List.of(), reason); }
    }

    /** Every due date from {@code from} to {@code to}, both included. */
    public static Result between(Rule r, Inputs in, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) throw new IllegalArgumentException("The window is not valid");
        if (to.isAfter(from.plusMonths(MAX_HORIZON_MONTHS))) throw new IllegalArgumentException("Look ahead no further than " + MAX_HORIZON_MONTHS + " months");
        List<LocalDate> out = new ArrayList<>();
        switch (r.type()) {
            case "NONE" -> { return Result.missing("No due date is set on this obligation"); }
            case "ANNUAL_FIXED" -> {
                for (int y = from.getYear() - 1; y <= to.getYear() + 1; y++) out.add(LocalDate.of(y, r.month(), Math.min(r.day(), YearMonth.of(y, r.month()).lengthOfMonth())));
            }
            case "MONTHLY_DAY" -> {
                for (YearMonth m = YearMonth.from(from).minusMonths(1); !m.isAfter(YearMonth.from(to).plusMonths(1)); m = m.plusMonths(1)) {
                    out.add(m.atDay(Math.min(r.day(), m.lengthOfMonth())));
                }
            }
            case "ANNUAL_AFTER_FYE" -> {
                if (in.financialYearEndMonth() == null) return Result.missing("Needs the client's financial year end month");
                for (int y = from.getYear() - 3; y <= to.getYear() + 1; y++) {
                    out.add(YearMonth.of(y, in.financialYearEndMonth()).plusMonths(r.offsetMonths()).atEndOfMonth());
                }
            }
            case "QUARTER_END_PLUS" -> {
                for (int y = from.getYear() - 1; y <= to.getYear() + 1; y++) {
                    for (int m : new int[]{3, 6, 9, 12}) out.add(YearMonth.of(y, m).atEndOfMonth().plusDays(r.offsetDays()));
                }
            }
            case "ANNIVERSARY" -> {
                if (in.registeredOn() == null) return Result.missing("Needs the date the client was registered");
                for (int y = from.getYear() - 3; y <= to.getYear() + 1; y++) {
                    if (y < in.registeredOn().getYear()) continue;
                    LocalDate anniversary = in.registeredOn().withYear(y);
                    out.add(anniversary.plusMonths(r.offsetMonths()));
                }
            }
            case "AFTER_EVENT" -> { return Result.missing("Depends on an event date, which has to be entered by hand"); }
            case "BEFORE_EXPIRY" -> { return Result.missing("Depends on an expiry date, which has to be entered by hand"); }
            default -> throw new IllegalArgumentException("Unknown due rule " + r.type());
        }
        List<LocalDate> inWindow = out.stream().filter(d -> !d.isBefore(from) && !d.isAfter(to)).distinct().sorted().toList();
        return Result.of(inWindow);
    }

    /** What the key dates have to look like. */
    public static void keyDates(Integer financialYearEndMonth, LocalDate registeredOn, LocalDate today) {
        if (financialYearEndMonth != null && (financialYearEndMonth < 1 || financialYearEndMonth > 12)) throw new IllegalArgumentException("The financial year end month must be 1 to 12");
        if (registeredOn != null && registeredOn.isAfter(today)) throw new IllegalArgumentException("The registration date cannot be in the future");
    }

    /**
     * Pending generated deadlines that should now go: the obligation still applies but a date is no longer wanted, or it stopped applying.
     * Dates already done or missed are never touched.
     */
    public static List<LocalDate> toCancel(List<LocalDate> existingPending, List<LocalDate> wanted) {
        return existingPending.stream().filter(d -> !wanted.contains(d)).sorted().toList();
    }

    public static List<LocalDate> toCreate(List<LocalDate> existingAny, List<LocalDate> wanted) {
        return wanted.stream().filter(d -> !existingAny.contains(d)).sorted().toList();
    }
}

package za.co.handyflow.platform.earthmoving.domain.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Which days of a calendar month a deployment is billed for by the monthly run. Pure rules: no database, no clock. */
public final class BillingRules {
    private BillingRules() {}

    public static final String READY = "READY";
    public static final String BILLED = "ALREADY_BILLED";
    public static final String EXCEPTION = "EXCEPTION";

    public record Span(LocalDate from, LocalDate to) {}

    /** "2026-09" to a month. */
    public static YearMonth month(String text) {
        try { return YearMonth.parse(text == null ? "" : text.trim()); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException("Give the month as year-month, for example 2026-09."); }
    }

    /** A month is billed only once it is over, so the readings and shifts for all of it are in. */
    public static void requireEnded(YearMonth month, LocalDate today) {
        if (!month.atEndOfMonth().isBefore(today))
            throw new IllegalArgumentException(month + " has not ended yet. Bill a month after its last day.");
    }

    /** The days of the month the machine was on this deployment, or null if it was not on hire at all in that month. */
    public static Span span(YearMonth month, LocalDate deployedOn, LocalDate returnedOn) {
        LocalDate first = month.atDay(1), last = month.atEndOfMonth();
        LocalDate from = deployedOn.isAfter(first) ? deployedOn : first;
        LocalDate to = returnedOn != null && returnedOn.isBefore(last) ? returnedOn : last;
        return to.isBefore(from) ? null : new Span(from, to);
    }

    public enum Cover { NONE, PARTIAL, FULL }

    /** Whether charges already cover none, some or all of the days in the span. */
    public static Cover cover(Span span, List<Span> charged) {
        int days = 0, covered = 0;
        for (LocalDate d = span.from(); !d.isAfter(span.to()); d = d.plusDays(1)) {
            days++;
            final LocalDate day = d;
            if (charged.stream().anyMatch(c -> !day.isBefore(c.from()) && !day.isAfter(c.to()))) covered++;
        }
        return covered == 0 ? Cover.NONE : covered == days ? Cover.FULL : Cover.PARTIAL;
    }

    /** The invoice title for a month's draft invoices. */
    public static String title(YearMonth month) {
        String name = month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);
        return "Plant hire " + name + " " + month.getYear();
    }
}

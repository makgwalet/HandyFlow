package za.co.handyflow.platform.warehousing.application.internal;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * The billing month a usage count belongs to. A tenant's Warehousing subscription renews on a day of the month (its billing anchor); the month runs from that
 * day to the day before the next one. An anchor later than a short month's last day (31 in February) renews on that month's last day. Pure: no database.
 */
public final class WhseUsagePeriod {
    private WhseUsagePeriod() {}

    /** The period starts on {@code start} and ends before {@code endExclusive}. */
    public record Period(LocalDate start, LocalDate endExclusive) {}

    private static LocalDate renewal(YearMonth ym, int anchor) {
        return ym.atDay(Math.min(anchor, ym.lengthOfMonth()));
    }

    /** The anchor is a day of the month, 1 to 31; anything else (or none) is treated as the 1st, which is the calendar month. */
    public static Period containing(LocalDate today, Integer anchor) {
        int a = anchor == null || anchor < 1 || anchor > 31 ? 1 : anchor;
        YearMonth ym = YearMonth.from(today);
        LocalDate thisRenewal = renewal(ym, a);
        LocalDate start = today.isBefore(thisRenewal) ? renewal(ym.minusMonths(1), a) : thisRenewal;
        LocalDate next = renewal(YearMonth.from(start).plusMonths(1), a);
        return new Period(start, next);
    }
}

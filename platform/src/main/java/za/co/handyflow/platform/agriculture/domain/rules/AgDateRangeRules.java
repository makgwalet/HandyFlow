package za.co.handyflow.platform.agriculture.domain.rules;

import java.time.LocalDate;

/**
 * An optional from/to date filter on a list. The queries never take a null date (the database cannot tell the type of a bare null),
 * so a missing end is replaced by a day far enough out that it never excludes anything. Both ends are included.
 */
public final class AgDateRangeRules {

    /** Used when no "from" is given: before any farm record. */
    public static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    /** Used when no "to" is given: after any farm record. */
    public static final LocalDate LATEST = LocalDate.of(2999, 12, 31);

    private AgDateRangeRules() {}

    public static LocalDate from(LocalDate from) { return from == null ? EARLIEST : from; }

    public static LocalDate to(LocalDate to) { return to == null ? LATEST : to; }

    /** True when neither end is given, so the plain, unfiltered query can be used. */
    public static boolean isUnbounded(LocalDate from, LocalDate to) { return from == null && to == null; }

    /** Both ends, when given, must be in order (the same day is allowed). */
    public static void require(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("The 'from' date must not be after the 'to' date");
    }
}

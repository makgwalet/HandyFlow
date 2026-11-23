package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure rules for the contract rate of a site. One rate applies on any day. Changing the price ends the old rate and adds a new one,
 * so what a client was charged in the past can always be explained. Dates are calendar days, both ends inclusive; no end date means "until further notice".
 */
final class SiteRateRules {

    static final String HOURLY = "HOURLY";
    static final String MONTHLY_FIXED = "MONTHLY_FIXED";
    /** R100 000 per guard hour: far above any real rate, so a slipped decimal point is refused rather than invoiced. */
    static final long MAX_HOURLY_CENTS = 10_000_000L;
    /** R10 million a month. */
    static final long MAX_MONTHLY_CENTS = 1_000_000_000L;
    static final int MIN_YEAR = 2000;
    static final int MAX_YEAR = 2100;

    private SiteRateRules() {}

    record Rate(UUID id, String type, long cents, LocalDate from, LocalDate to) {}

    /** What has to happen to the existing rates before a new one is added: end `closeId` on `closeTo`, or nothing. */
    record Plan(UUID closeId, LocalDate closeTo) {}

    static void validate(String type, Long cents, LocalDate from, LocalDate to) {
        if (!HOURLY.equals(type) && !MONTHLY_FIXED.equals(type)) {
            throw bad("RATE_TYPE", "Rate type must be HOURLY or MONTHLY_FIXED.");
        }
        if (cents == null || cents <= 0) throw bad("RATE_AMOUNT", "The rate must be more than zero.");
        long max = HOURLY.equals(type) ? MAX_HOURLY_CENTS : MAX_MONTHLY_CENTS;
        if (cents > max) throw bad("RATE_TOO_HIGH", "That rate is higher than allowed. Amounts are in cents; check for an extra zero.");
        if (from == null) throw bad("RATE_FROM", "The date the rate starts is required.");
        if (from.getYear() < MIN_YEAR || from.getYear() > MAX_YEAR) throw bad("RATE_DATE", "The start date is not a sensible date.");
        if (to != null) {
            if (to.isBefore(from)) throw bad("RATE_DATES", "The rate cannot end before it starts.");
            if (to.getYear() > MAX_YEAR) throw bad("RATE_DATE", "The end date is not a sensible date.");
        }
    }

    /**
     * Where a new rate may go. It may not overlap an existing rate, with one convenience: a new open-ended rate that starts
     * after the current open-ended rate began ends that rate the day before. Anything else must be ended explicitly first.
     */
    static Plan plan(List<Rate> existing, LocalDate from, LocalDate to) {
        UUID close = null;
        for (Rate e : existing) {
            if (!overlaps(e.from(), e.to(), from, to)) continue;
            boolean replacesOpenRate = e.to() == null && to == null && e.from().isBefore(from) && close == null;
            if (!replacesOpenRate) {
                throw new HandyFlowException("A rate already covers " + e.from() + (e.to() == null ? " onwards" : " to " + e.to())
                        + ". End it first, or choose dates that do not overlap.", HttpStatus.CONFLICT, "RATE_OVERLAP");
            }
            close = e.id();
        }
        return close == null ? new Plan(null, null) : new Plan(close, from.minusDays(1));
    }

    private static boolean overlaps(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        boolean aStartsBeforeBEnds = bTo == null || !aFrom.isAfter(bTo);
        boolean bStartsBeforeAEnds = aTo == null || !bFrom.isAfter(aTo);
        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }

    /** The rate in force on a day, if any. */
    static Optional<Rate> rateOn(List<Rate> rates, LocalDate day) {
        if (day == null) return Optional.empty();
        return rates.stream()
                .filter(r -> !day.isBefore(r.from()) && (r.to() == null || !day.isAfter(r.to())))
                .max(Comparator.comparing(Rate::from));
    }

    /** Only an open-ended rate can be ended, and not before it started. */
    static LocalDate requireEndable(Rate rate, LocalDate end) {
        if (rate == null) throw new HandyFlowException("Rate not found", HttpStatus.NOT_FOUND, "RATE_NOT_FOUND");
        if (end == null) throw bad("RATE_END", "The last day of the rate is required.");
        if (rate.to() != null) throw new HandyFlowException("That rate has already ended.", HttpStatus.CONFLICT, "RATE_ALREADY_ENDED");
        if (end.isBefore(rate.from())) throw bad("RATE_DATES", "The rate cannot end before it starts.");
        if (end.getYear() > MAX_YEAR) throw bad("RATE_DATE", "The end date is not a sensible date.");
        return end;
    }

    private static HandyFlowException bad(String code, String message) {
        return new HandyFlowException(message, HttpStatus.BAD_REQUEST, code);
    }
}

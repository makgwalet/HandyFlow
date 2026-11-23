package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure rules for pricing a month at a site from its contract rates and the shifts worked. This is a preview, not an invoice:
 * only COMPLETED shifts are priced, every other shift is counted but not billed, and no VAT is calculated.
 *
 * <p>Hours are the part of each shift inside the month (a night shift over month end is split). An hourly shift is priced at the rate in
 * force on the day it <em>starts</em> in the company's zone. A monthly fixed rate is charged by calendar day in force
 * (amount × days in force ÷ days in the month), and shifts worked under it add hours but no money.
 */
final class BillingRules {

    enum Basis {
        /** Hours as scheduled: how weekly hours and payroll count today. */
        SCHEDULED,
        /** Hours as clocked (actual start to actual end) where both are recorded, otherwise as scheduled. */
        ACTUAL;

        static Basis parse(String asked) {
            if (asked == null || asked.isBlank()) return SCHEDULED;
            try { return valueOf(asked.strip().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException e) {
                throw new HandyFlowException("Basis must be SCHEDULED or ACTUAL.", HttpStatus.BAD_REQUEST, "BILLING_BASIS");
            }
        }
    }

    static final String BILLED_STATUS = "COMPLETED";

    record RateInfo(UUID id, String type, long cents, boolean vatInclusive, LocalDate from, LocalDate to) {}

    record Shift(Instant start, Instant end, Instant actualStart, Instant actualEnd, String status) {}

    record Line(UUID rateId, String type, long rateCents, boolean vatInclusive, long minutes, long days, long amountCents) {}

    record Preview(YearMonth month, Basis basis, List<Line> lines, long billedMinutes, long scheduledMinutes, long actualMinutes,
                   long unpricedMinutes, long fixedFeeMinutes, long shiftsWithoutActual, Map<String, Integer> notBilled,
                   boolean mixedVat, long totalCents) {}

    private BillingRules() {}

    static Preview price(YearMonth month, ZoneId zone, Basis basis, List<Shift> shifts, List<RateInfo> rates) {
        Instant w0 = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant w1 = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        int daysInMonth = month.lengthOfMonth();

        Map<UUID, Long> hourlyMinutes = new LinkedHashMap<>();
        Map<UUID, Long> fixedMinutes = new LinkedHashMap<>();
        Map<String, Integer> notBilled = new LinkedHashMap<>();
        long scheduled = 0, actual = 0, billed = 0, unpriced = 0, fixed = 0, withoutActual = 0;

        for (Shift s : shifts) {
            if (!BILLED_STATUS.equals(s.status())) {
                notBilled.merge(s.status() == null ? "UNKNOWN" : s.status(), 1, Integer::sum);
                continue;
            }
            long sched = overlapMinutes(s.start(), s.end(), w0, w1);
            boolean hasActual = s.actualStart() != null && s.actualEnd() != null && s.actualEnd().isAfter(s.actualStart());
            long act = hasActual ? overlapMinutes(s.actualStart(), s.actualEnd(), w0, w1) : 0;
            scheduled += sched;
            if (hasActual) actual += act; else withoutActual++;
            long minutes = basis == Basis.ACTUAL && hasActual ? act : sched;
            if (minutes <= 0) continue;
            billed += minutes;
            RateInfo r = rateOn(rates, s.start().atZone(zone).toLocalDate());
            if (r == null) unpriced += minutes;
            else if (MONTHLY.equals(r.type())) { fixed += minutes; fixedMinutes.merge(r.id(), minutes, Long::sum); }
            else hourlyMinutes.merge(r.id(), minutes, Long::sum);
        }

        List<Line> lines = new ArrayList<>();
        LocalDate first = month.atDay(1), last = month.atEndOfMonth();
        for (RateInfo r : rates.stream().sorted(Comparator.comparing(RateInfo::from)).toList()) {
            if (MONTHLY.equals(r.type())) {
                long days = daysInForce(r, first, last);
                if (days <= 0) continue;
                lines.add(new Line(r.id(), r.type(), r.cents(), r.vatInclusive(), fixedMinutes.getOrDefault(r.id(), 0L), days,
                        roundedDiv(Math.multiplyExact(r.cents(), days), daysInMonth)));
            } else {
                Long minutes = hourlyMinutes.get(r.id());
                if (minutes == null) continue;
                lines.add(new Line(r.id(), r.type(), r.cents(), r.vatInclusive(), minutes, 0,
                        roundedDiv(Math.multiplyExact(r.cents(), minutes), 60)));
            }
        }
        long total = 0;
        boolean anyVat = false, anyNoVat = false;
        for (Line l : lines) {
            total = Math.addExact(total, l.amountCents());
            if (l.vatInclusive()) anyVat = true; else anyNoVat = true;
        }
        return new Preview(month, basis, List.copyOf(lines), billed, scheduled, actual, unpriced, fixed, withoutActual, notBilled, anyVat && anyNoVat, total);
    }

    static final String MONTHLY = "MONTHLY_FIXED";

    /** Whole minutes of [a, b) that fall inside [w0, w1). */
    static long overlapMinutes(Instant a, Instant b, Instant w0, Instant w1) {
        if (a == null || b == null) return 0;
        Instant from = a.isAfter(w0) ? a : w0;
        Instant to = b.isBefore(w1) ? b : w1;
        return to.isAfter(from) ? Duration.between(from, to).toMinutes() : 0;
    }

    static RateInfo rateOn(List<RateInfo> rates, LocalDate day) {
        RateInfo best = null;
        for (RateInfo r : rates) {
            boolean in = !day.isBefore(r.from()) && (r.to() == null || !day.isAfter(r.to()));
            if (in && (best == null || r.from().isAfter(best.from()))) best = r;
        }
        return best;
    }

    /** Calendar days of [first, last] in which the rate is in force, both ends inclusive. */
    static long daysInForce(RateInfo r, LocalDate first, LocalDate last) {
        LocalDate from = r.from().isAfter(first) ? r.from() : first;
        LocalDate to = r.to() == null || r.to().isAfter(last) ? last : r.to();
        return to.isBefore(from) ? 0 : to.toEpochDay() - from.toEpochDay() + 1;
    }

    /** a ÷ b rounded half up, for non-negative whole numbers. */
    static long roundedDiv(long a, long b) {
        return Math.addExact(Math.multiplyExact(a, 2L), b) / Math.multiplyExact(b, 2L);
    }
}

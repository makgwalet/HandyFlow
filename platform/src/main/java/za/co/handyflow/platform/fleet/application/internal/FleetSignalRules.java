package za.co.handyflow.platform.fleet.application.internal;

import java.time.LocalDate;
import java.util.List;

/**
 * Counts vehicle compliance dates that have lapsed or are about to. Uses the dates on the vehicle itself (licence disc, roadworthy, insurance), which is the same
 * source the Fleet compliance board and its expiry alerts use. A missing date is not counted: "not recorded" is not the same as "expired".
 */
final class FleetSignalRules {

    private FleetSignalRules() {}

    static final int WARNING_DAYS = 30;

    record Dates(LocalDate licenceDisc, LocalDate roadworthy, LocalDate insurance) {}

    record Counts(long expired, long expiring) {}

    static Counts count(List<Dates> vehicles, LocalDate today) {
        LocalDate until = today.plusDays(WARNING_DAYS);
        long expired = 0, expiring = 0;
        for (Dates d : vehicles)
            for (LocalDate e : new LocalDate[]{d.licenceDisc(), d.roadworthy(), d.insurance()}) {
                if (e == null) continue;
                if (e.isBefore(today)) expired++;
                else if (!e.isAfter(until)) expiring++;
            }
        return new Counts(expired, expiring);
    }
}

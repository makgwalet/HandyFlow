package za.co.handyflow.platform.fleet.application.internal;

import java.util.List;

/**
 * Computed totals for a logbook period — shared between the PDF and Excel
 * generators so the two formats can never disagree with each other about
 * the numbers, since both are built from this one calculation.
 */
record LogbookSummary(
        Integer openingOdometer,
        Integer closingOdometer,
        int totalKm,
        int businessKm,
        int privateKm,
        double businessPercent,
        int tripCount
) {
    static LogbookSummary from(List<za.co.handyflow.platform.fleet.domain.model.Trip> trips) {
        if (trips.isEmpty()) {
            return new LogbookSummary(null, null, 0, 0, 0, 0.0, 0);
        }
        Integer opening = trips.get(0).getStartOdometer();
        Integer closing = trips.get(trips.size() - 1).getEndOdometer();

        int business = 0, priv = 0;
        for (var t : trips) {
            Integer km = t.getDistanceKm();
            if (km == null) continue;
            if ("PRIVATE".equalsIgnoreCase(t.getTripType())) priv += km;
            else business += km;
        }
        int total = business + priv;
        double pct = total > 0 ? (business * 100.0) / total : 0.0;

        return new LogbookSummary(opening, closing, total, business, priv, pct, trips.size());
    }
}

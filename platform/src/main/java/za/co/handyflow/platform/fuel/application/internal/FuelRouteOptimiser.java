package za.co.handyflow.platform.fuel.application.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

/**
 * Phase 8b: suggests a stop order for a route. Pure calculation, no database and no network.
 * <ul>
 *   <li>Only movable stops (still scheduled) are reordered; a stop on the road or delivered keeps its place, as the route rules require.</li>
 *   <li>STRAIGHT_LINE (every movable stop has coordinates): cheapest insertion then 2-opt on straight-line distance, which is shorter than the road distance and
 *       says nothing about roads or traffic. Stops with a delivery window keep their window order (earliest window end first); a change that would swap two of them is rejected.</li>
 *   <li>WINDOWS_AND_DISTANCE (some coordinates missing): stops with a window first, earliest window end first, then the rest nearest first by the stored site distance.</li>
 *   <li>The start point is optional. When given, the route is measured from it and back to it.</li>
 * </ul>
 * It never decides anything: the planner sees the suggestion and applies it with the normal stop-order call, or ignores it.
 */
public final class FuelRouteOptimiser {
    private FuelRouteOptimiser() {}

    public static final String STRAIGHT_LINE = "STRAIGHT_LINE";
    public static final String WINDOWS_AND_DISTANCE = "WINDOWS_AND_DISTANCE";
    private static final double EARTH_KM = 6371.0088;

    public record Stop(UUID id, boolean movable, BigDecimal lat, BigDecimal lng, BigDecimal distanceKm, Instant windowStart, Instant windowEnd) {
        boolean hasPoint() { return lat != null && lng != null; }
    }

    /** order = every stop id in the suggested order. km figures are null unless the method is STRAIGHT_LINE; they cover the movable stops only. */
    public record Result(List<UUID> order, String method, BigDecimal currentKm, BigDecimal suggestedKm, boolean changed) {}

    public static Result optimise(List<Stop> current, BigDecimal startLat, BigDecimal startLng) {
        List<Stop> movable = new ArrayList<>();
        for (Stop s : current) if (s.movable()) movable.add(s);
        boolean straight = !movable.isEmpty() && movable.stream().allMatch(Stop::hasPoint);
        boolean startGiven = startLat != null && startLng != null;
        double[] anchor = null;
        if (startGiven) anchor = new double[]{startLat.doubleValue(), startLng.doubleValue()};
        else {
            // otherwise the last fixed stop that sits before the first movable one, if it has a point
            Stop before = null;
            for (Stop s : current) { if (s.movable()) break; before = s; }
            if (before != null && before.hasPoint()) anchor = new double[]{before.lat().doubleValue(), before.lng().doubleValue()};
        }
        boolean closed = startGiven;

        List<Stop> ordered;
        if (movable.size() < 2) ordered = movable;
        else if (straight) ordered = straightLine(movable, anchor, closed);
        else ordered = windowsAndDistance(movable);

        // put the ordered movable stops back into the movable slots
        List<UUID> out = new ArrayList<>();
        Iterator<Stop> it = ordered.iterator();
        for (Stop s : current) out.add(s.movable() ? it.next().id() : s.id());
        boolean changed = !out.equals(current.stream().map(Stop::id).toList());
        BigDecimal currentKm = null, suggestedKm = null;
        if (straight) {
            currentKm = km(length(movable, anchor, closed));
            suggestedKm = km(length(ordered, anchor, closed));
        }
        return new Result(out, straight ? STRAIGHT_LINE : WINDOWS_AND_DISTANCE, currentKm, suggestedKm, changed);
    }

    // ── straight line ────────────────────────────────────────────────────────

    private static List<Stop> straightLine(List<Stop> movable, double[] anchor, boolean closed) {
        List<Stop> timed = new ArrayList<>(), untimed = new ArrayList<>();
        for (Stop s : movable) (s.windowEnd() != null ? timed : untimed).add(s);
        Comparator<Stop> byWindow = Comparator.comparing(Stop::windowEnd)
                .thenComparing(Stop::windowStart, Comparator.nullsLast(Comparator.naturalOrder()));
        timed.sort(byWindow); // stable, so ties keep the current order
        List<UUID> timedOrder = timed.stream().map(Stop::id).toList();

        List<Stop> tour = new ArrayList<>(timed);
        List<Stop> rest = new ArrayList<>(untimed);
        if (tour.isEmpty() && !rest.isEmpty()) {
            Stop first = rest.get(0);
            if (anchor != null) { for (Stop s : rest) if (dist(anchor, s) < dist(anchor, first)) first = s; }
            else for (Stop s : rest) if (distKm(s) < distKm(first)) first = s;
            rest.remove(first); tour.add(first);
        }
        for (Stop s : rest) { // cheapest insertion; never moves a timed stop relative to another
            int bestAt = 0; double best = Double.MAX_VALUE;
            for (int p = 0; p <= tour.size(); p++) {
                List<Stop> cand = new ArrayList<>(tour); cand.add(p, s);
                double len = length(cand, anchor, closed);
                if (len < best - 1e-9) { best = len; bestAt = p; }
            }
            tour.add(bestAt, s);
        }
        // 2-opt, accepting a reversal only if it is shorter and keeps the window order
        boolean improved = true; int passes = 0;
        while (improved && passes++ < 100) {
            improved = false;
            double len = length(tour, anchor, closed);
            for (int i = 0; i < tour.size() - 1 && !improved; i++) {
                for (int j = i + 1; j < tour.size() && !improved; j++) {
                    List<Stop> cand = new ArrayList<>(tour);
                    Collections.reverse(cand.subList(i, j + 1));
                    if (!keepsWindowOrder(cand, timedOrder)) continue;
                    double l = length(cand, anchor, closed);
                    if (l < len - 1e-9) { tour = cand; improved = true; }
                }
            }
        }
        // never suggest something longer than what the planner already has (keeping window order is a rule, so compare only when the current order obeys it)
        List<Stop> keep = new ArrayList<>(movable);
        if (keepsWindowOrder(keep, timedOrder) && length(keep, anchor, closed) <= length(tour, anchor, closed) + 1e-9) return keep;
        return tour;
    }

    private static boolean keepsWindowOrder(List<Stop> tour, List<UUID> timedOrder) {
        int k = 0;
        for (Stop s : tour) {
            if (s.windowEnd() == null) continue;
            if (k >= timedOrder.size() || !timedOrder.get(k).equals(s.id())) return false;
            k++;
        }
        return true;
    }

    // ── fallback ─────────────────────────────────────────────────────────────

    private static List<Stop> windowsAndDistance(List<Stop> movable) {
        List<Stop> out = new ArrayList<>(movable);
        Comparator<Stop> c = Comparator
                .comparing((Stop s) -> s.windowEnd() == null ? 1 : 0)
                .thenComparing(Stop::windowEnd, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(s -> s.distanceKm() == null ? Double.MAX_VALUE : s.distanceKm().doubleValue());
        out.sort(c); // stable
        return out;
    }

    // ── geometry ─────────────────────────────────────────────────────────────

    private static double distKm(Stop s) { return s.distanceKm() == null ? Double.MAX_VALUE : s.distanceKm().doubleValue(); }

    private static double dist(double[] a, Stop s) { return haversine(a[0], a[1], s.lat().doubleValue(), s.lng().doubleValue()); }

    private static double length(List<Stop> tour, double[] anchor, boolean closed) {
        if (tour.isEmpty()) return 0;
        double total = 0;
        double[] prev = anchor;
        for (Stop s : tour) {
            double[] p = {s.lat().doubleValue(), s.lng().doubleValue()};
            if (prev != null) total += haversine(prev[0], prev[1], p[0], p[1]);
            prev = p;
        }
        if (closed && anchor != null) total += haversine(prev[0], prev[1], anchor[0], anchor[1]);
        return total;
    }

    static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    private static BigDecimal km(double v) { return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP); }
}

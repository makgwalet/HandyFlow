package za.co.handyflow.platform.fleet.application.internal;

import za.co.handyflow.platform.fleet.domain.model.FuelFillup;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fuel consumption analytics as pure functions over fill-up lists — no repositories, no Spring.
 * <h3>Consumption: the full-tank-to-full-tank method</h3>
 * Consumption is only measurable between two FULL fill-ups with odometer readings: the fuel put in
 * after the first full fill-up, up to and including the second, is what the vehicle burned over the
 * distance between them. Partial fills in between add to the litres. Fill-ups with no odometer, or
 * partial fills before the first full one, can't anchor a measurement and are skipped.
 * <h3>"Abnormal"</h3>
 * Each measured segment is compared with a baseline: the vehicle's manual expected figure if one is set,
 * otherwise the median of its previous {@value #ROLLING_WINDOW} assessable segments (needing at least
 * {@value #MIN_BASELINE_SEGMENTS}). More than {@code tolerance}% above baseline is HIGH_CONSUMPTION;
 * more than tolerance + {@value #LOW_EXTRA_PERCENT}% below it is LOW_CONSUMPTION (which usually
 * means a missing fill-up record or an odometer error, not a miracle). The median keeps one bad
 * segment from dragging the baseline. Known blind spot: a vehicle that is <i>always</i> inefficient
 * is never flagged by its own history — set an expected figure for those.
 * <h3>Indicators</h3>
 * Heuristics that may point to theft or bad data — never proof. See {@link IndicatorType}.
 */
final class FuelAnalyticsCalculator {

    static final int MIN_SEGMENT_KM = 50;
    static final double MIN_PLAUSIBLE_L100 = 2.0;
    static final double MAX_PLAUSIBLE_L100 = 120.0;
    static final int ROLLING_WINDOW = 5;
    static final int MIN_BASELINE_SEGMENTS = 3;
    static final int DEFAULT_TOLERANCE_PERCENT = 20;
    static final int LOW_EXTRA_PERCENT = 10;
    /** A single fill-up above tank capacity by more than this factor is physically implausible. */
    static final double OVERFILL_FACTOR = 1.05;
    /** With no tank capacity on file, litres at or above this with no distance travelled are flagged. */
    static final double NO_DISTANCE_FALLBACK_LITRES = 15.0;
    static final double NO_DISTANCE_CAPACITY_FRACTION = 0.20;

    private FuelAnalyticsCalculator() {}

    enum SegmentStatus { NORMAL, HIGH_CONSUMPTION, LOW_CONSUMPTION, NO_BASELINE, NOT_ASSESSED, DATA_SUSPECT }

    enum BaselineSource { MANUAL, ROLLING_MEDIAN, NONE }

    enum Severity { INFO, WARNING, ALERT }

    enum IndicatorType {
        /** One fill-up holds more fuel than the tank can. Wrong tank capacity on file, or a receipt that isn't for this vehicle. */
        OVERFILL,
        /** Several fill-ups on one day total more than the tank holds. */
        SAME_DAY_MULTIPLE,
        /** The same receipt reference appears on more than one fill-up. */
        DUPLICATE_RECEIPT,
        /** Fuel added but the odometer has not moved since the previous fill-up. */
        NO_DISTANCE,
        /** The odometer is lower than at an earlier fill-up. Data quality, and a common cover for theft. */
        ODOMETER_REGRESSION
    }

    /** One measured stretch between two full fill-ups. {@code cost} is null if any fill-up in it had no cost. */
    record Segment(FuelFillup start, FuelFillup end, int km, double litres, int partialFills, BigDecimal cost,
                   double litresPer100Km, Double baseline, BaselineSource baselineSource, Double deviationPercent,
                   SegmentStatus status) {

        LocalDate startDate() { return start.getFilledAt(); }
        LocalDate endDate() { return end.getFilledAt(); }
        double kmPerLitre() { return km / litres; }

        boolean isAbnormal() {
            return status == SegmentStatus.HIGH_CONSUMPTION || status == SegmentStatus.LOW_CONSUMPTION
                    || status == SegmentStatus.DATA_SUSPECT;
        }

        /** Counts toward averages: long enough to be meaningful and not implausible. */
        boolean isAssessable() {
            return status != SegmentStatus.NOT_ASSESSED && status != SegmentStatus.DATA_SUSPECT;
        }

        String describe() {
            return switch (status) {
                case HIGH_CONSUMPTION -> String.format(Locale.ROOT,
                        "%.1f L/100 km to %s is %.0f%% above the baseline of %.1f", litresPer100Km, endDate(),
                        deviationPercent, baseline);
                case LOW_CONSUMPTION -> String.format(Locale.ROOT,
                        "%.1f L/100 km to %s is %.0f%% below the baseline of %.1f — check for a missing fill-up or odometer error",
                        litresPer100Km, endDate(), Math.abs(deviationPercent), baseline);
                case DATA_SUSPECT -> String.format(Locale.ROOT,
                        "%.1f L/100 km to %s is implausible — check odometer readings and litres", litresPer100Km, endDate());
                default -> String.format(Locale.ROOT, "%.1f L/100 km to %s", litresPer100Km, endDate());
            };
        }
    }

    record Indicator(IndicatorType type, Severity severity, FuelFillup fillup, String message) {}

    // ── Segments ─────────────────────────────────────────────────────────

    /**
     * Measures consumption for a vehicle. {@code fillups} must be oldest first (date, then odometer).
     * {@code expectedLitresPer100Km} (nullable) is the manual yardstick; {@code tolerancePercent}
     * how far from it counts as abnormal.
     */
    static List<Segment> segments(List<FuelFillup> fillups, Double expectedLitresPer100Km, int tolerancePercent) {
        List<RawSegment> raw = rawSegments(fillups);

        List<Segment> out = new ArrayList<>();
        List<Double> history = new ArrayList<>();   // l/100km of previous assessable segments, oldest first
        for (RawSegment r : raw) {
            double l100 = r.litres / r.km * 100.0;
            Double baseline = null;
            BaselineSource source = BaselineSource.NONE;
            Double deviation = null;
            SegmentStatus status;

            if (r.km < MIN_SEGMENT_KM) {
                status = SegmentStatus.NOT_ASSESSED;
            } else if (l100 < MIN_PLAUSIBLE_L100 || l100 > MAX_PLAUSIBLE_L100) {
                status = SegmentStatus.DATA_SUSPECT;
            } else {
                if (expectedLitresPer100Km != null) {
                    baseline = expectedLitresPer100Km;
                    source = BaselineSource.MANUAL;
                } else if (history.size() >= MIN_BASELINE_SEGMENTS) {
                    List<Double> recent = history.subList(Math.max(0, history.size() - ROLLING_WINDOW), history.size());
                    baseline = median(recent);
                    source = BaselineSource.ROLLING_MEDIAN;
                }
                if (baseline == null) {
                    status = SegmentStatus.NO_BASELINE;
                } else {
                    deviation = (l100 - baseline) / baseline * 100.0;
                    if (deviation > tolerancePercent) status = SegmentStatus.HIGH_CONSUMPTION;
                    else if (deviation < -(tolerancePercent + LOW_EXTRA_PERCENT)) status = SegmentStatus.LOW_CONSUMPTION;
                    else status = SegmentStatus.NORMAL;
                }
                history.add(l100);
            }
            out.add(new Segment(r.start, r.end, r.km, r.litres, r.partials, r.cost, l100, baseline, source,
                    deviation, status));
        }
        return out;
    }

    private record RawSegment(FuelFillup start, FuelFillup end, int km, double litres, int partials, BigDecimal cost) {}

    private static List<RawSegment> rawSegments(List<FuelFillup> fillups) {
        List<RawSegment> out = new ArrayList<>();
        FuelFillup anchor = null;
        double litres = 0;
        int partials = 0;
        BigDecimal cost = BigDecimal.ZERO;
        boolean costKnown = true;

        for (FuelFillup f : fillups) {
            Integer odo = f.getOdometerAtFillup();
            if (f.getLitres() == null || f.getLitres().signum() <= 0) continue;

            if (anchor == null) {
                if (f.isFullTank() && odo != null) {
                    anchor = f;
                    litres = 0; partials = 0; cost = BigDecimal.ZERO; costKnown = true;
                }
                continue;
            }
            if (odo != null && odo < anchor.getOdometerAtFillup()) {
                // Odometer went backwards: this reading can't be trusted. A full fill-up restarts the chain.
                if (f.isFullTank()) {
                    anchor = f;
                    litres = 0; partials = 0; cost = BigDecimal.ZERO; costKnown = true;
                }
                continue;
            }

            litres += f.getLitres().doubleValue();
            BigDecimal c = costOf(f);
            if (c == null) costKnown = false; else cost = cost.add(c);

            if (f.isFullTank() && odo != null) {
                int km = odo - anchor.getOdometerAtFillup();
                if (km > 0 && litres > 0) {
                    out.add(new RawSegment(anchor, f, km, litres, partials, costKnown ? cost : null));
                }
                anchor = f;
                litres = 0; partials = 0; cost = BigDecimal.ZERO; costKnown = true;
            } else if (f.isFullTank()) {
                // Full fill-up with no odometer: we no longer know where the chain stands.
                anchor = null;
            } else {
                partials++;
            }
        }
        return out;
    }

    // ── Indicators ───────────────────────────────────────────────────────

    /**
     * Theft / data-quality indicators for one vehicle's fill-ups in [from, to].
     * {@code fillups} must be oldest first and may extend before {@code from} (for the
     * "previous odometer" comparison). {@code receiptCounts} is how many fill-ups in the whole
     * tenant carry each upper-cased, trimmed receipt reference.
     */
    static List<Indicator> indicators(List<FuelFillup> fillups, BigDecimal tankCapacityLitres,
                                      Map<String, Long> receiptCounts, LocalDate from, LocalDate to) {
        List<Indicator> out = new ArrayList<>();
        Double capacity = tankCapacityLitres != null && tankCapacityLitres.signum() > 0
                ? tankCapacityLitres.doubleValue() : null;

        FuelFillup previousWithOdometer = null;
        Map<LocalDate, List<FuelFillup>> inWindowByDate = new HashMap<>();

        for (FuelFillup f : fillups) {
            boolean inWindow = !f.getFilledAt().isBefore(from) && !f.getFilledAt().isAfter(to);
            double litres = f.getLitres() != null ? f.getLitres().doubleValue() : 0;

            if (inWindow) {
                inWindowByDate.computeIfAbsent(f.getFilledAt(), d -> new ArrayList<>()).add(f);

                if (capacity != null && litres > capacity * OVERFILL_FACTOR) {
                    out.add(new Indicator(IndicatorType.OVERFILL, Severity.WARNING, f, String.format(Locale.ROOT,
                            "%.1f L put into a %.0f L tank", litres, capacity)));
                }
                String ref = normaliseReceipt(f.getReceiptRef());
                if (ref != null && receiptCounts.getOrDefault(ref, 1L) > 1) {
                    out.add(new Indicator(IndicatorType.DUPLICATE_RECEIPT, Severity.ALERT, f,
                            "Receipt " + ref + " appears on " + receiptCounts.get(ref) + " fill-ups"));
                }
                if (f.getOdometerAtFillup() != null && previousWithOdometer != null) {
                    int prev = previousWithOdometer.getOdometerAtFillup();
                    if (f.getOdometerAtFillup() < prev) {
                        out.add(new Indicator(IndicatorType.ODOMETER_REGRESSION, Severity.WARNING, f,
                                "Odometer " + f.getOdometerAtFillup() + " is lower than " + prev
                                        + " at the fill-up on " + previousWithOdometer.getFilledAt()));
                    } else if (f.getOdometerAtFillup() == prev
                            && litres >= (capacity != null ? capacity * NO_DISTANCE_CAPACITY_FRACTION
                            : NO_DISTANCE_FALLBACK_LITRES)) {
                        out.add(new Indicator(IndicatorType.NO_DISTANCE, Severity.WARNING, f, String.format(Locale.ROOT,
                                "%.1f L added but the odometer has not moved since %s", litres,
                                previousWithOdometer.getFilledAt())));
                    }
                }
            }
            // One bad (lower) reading must not poison the comparison for the fill-ups after it.
            if (f.getOdometerAtFillup() != null && (previousWithOdometer == null
                    || f.getOdometerAtFillup() >= previousWithOdometer.getOdometerAtFillup())) {
                previousWithOdometer = f;
            }
        }

        if (capacity != null) {
            for (Map.Entry<LocalDate, List<FuelFillup>> e : inWindowByDate.entrySet()) {
                List<FuelFillup> day = e.getValue();
                double total = day.stream().filter(x -> x.getLitres() != null)
                        .mapToDouble(x -> x.getLitres().doubleValue()).sum();
                // Single over-capacity fills are OVERFILL already; this catches several smaller ones.
                boolean singleAlreadyFlagged = day.stream().anyMatch(x -> x.getLitres() != null
                        && x.getLitres().doubleValue() > capacity * OVERFILL_FACTOR);
                if (day.size() >= 2 && total > capacity * OVERFILL_FACTOR && !singleAlreadyFlagged) {
                    out.add(new Indicator(IndicatorType.SAME_DAY_MULTIPLE, Severity.WARNING, day.get(day.size() - 1),
                            String.format(Locale.ROOT, "%d fill-ups on %s total %.1f L in a %.0f L tank",
                                    day.size(), e.getKey(), total, capacity)));
                }
            }
        }
        out.sort((a, b) -> a.fillup().getFilledAt().compareTo(b.fillup().getFilledAt()));
        return out;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** What this fill-up cost: its recorded total, else price × litres, else unknown. */
    static BigDecimal costOf(FuelFillup f) {
        if (f.getTotalCost() != null) return f.getTotalCost();
        if (f.getPricePerLitre() != null && f.getLitres() != null) {
            return f.getPricePerLitre().multiply(f.getLitres()).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return null;
    }

    static String normaliseReceipt(String ref) {
        if (ref == null) return null;
        String t = ref.trim().toUpperCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    /** Counts fill-ups per normalised receipt reference. */
    static Map<String, Long> receiptCounts(List<FuelFillup> fillups) {
        Map<String, Long> counts = new HashMap<>();
        for (FuelFillup f : fillups) {
            String ref = normaliseReceipt(f.getReceiptRef());
            if (ref != null) counts.merge(ref, 1L, Long::sum);
        }
        return counts;
    }

    static double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    /** Groups a tenant-wide fill-up list by vehicle, keeping each group's incoming (oldest-first) order. */
    static Map<UUID, List<FuelFillup>> byVehicle(List<FuelFillup> fillups) {
        Map<UUID, List<FuelFillup>> map = new HashMap<>();
        for (FuelFillup f : fillups) {
            map.computeIfAbsent(f.getVehicleId(), k -> new ArrayList<>()).add(f);
        }
        return map;
    }
}
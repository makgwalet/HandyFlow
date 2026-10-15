package za.co.handyflow.platform.earthmoving.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Meter type and unit, and the rules for correcting a reading or changing the meter. */
public final class MeterRules {
    private MeterRules() {}

    public static final String HOURS = "HOURS";
    public static final String KILOMETRES = "KILOMETRES";
    public static final List<String> TYPES = List.of(HOURS, KILOMETRES);
    public static final List<String> SOURCES = List.of("MANUAL", "SHIFT", "SERVICE", "CORRECTION", "TYPE_CHANGE");

    /** Blank means hours (the default for every machine that existed before meter types). */
    public static String normalizeType(String type) {
        if (type == null || type.isBlank()) return HOURS;
        String t = type.trim().toUpperCase();
        if (!TYPES.contains(t)) throw new IllegalArgumentException("Meter type must be one of " + TYPES + ".");
        return t;
    }

    public static String unit(String type) { return KILOMETRES.equals(type) ? "km" : "hrs"; }
    public static String word(String type) { return KILOMETRES.equals(type) ? "kilometres" : "hours"; }

    /**
     * A correction fixes a reading that was entered wrongly. It is the only way a reading goes down, so it needs a reason,
     * must differ from the current reading, may not drop below the reading before the one it corrects (floor), and may
     * not leave the last service ahead of the meter.
     */
    public static String checkCorrection(BigDecimal current, BigDecimal corrected, BigDecimal floor, BigDecimal lastService, String reason) {
        if (corrected == null || corrected.signum() < 0) throw new IllegalArgumentException("Give the corrected reading (zero or more).");
        if (current != null && corrected.compareTo(current) == 0) throw new IllegalArgumentException("That is already the current reading.");
        String why = EarthmovingRules.requireReason(reason, "correcting a meter reading");
        if (floor != null && corrected.compareTo(floor) < 0)
            throw new IllegalArgumentException("The corrected reading (" + corrected + ") cannot be below the earlier reading of " + floor
                    + ". A meter only counts up, so fix the earlier reading first.");
        if (lastService != null && corrected.compareTo(lastService) < 0)
            throw new IllegalStateException("The last service was recorded at " + lastService + ", above the corrected reading. Correct the service record first.");
        return why;
    }

    /** Changing hours to kilometres (or back) changes what every number means, so all three numbers are given again. */
    public static String checkChange(String oldType, String newType, BigDecimal current, BigDecimal lastService, BigDecimal interval, String reason) {
        if (newType.equals(oldType)) throw new IllegalArgumentException("The meter is already in " + word(newType) + ".");
        if (current == null || current.signum() < 0) throw new IllegalArgumentException("Give the current reading in " + word(newType) + ".");
        if (lastService == null || lastService.signum() < 0 || lastService.compareTo(current) > 0)
            throw new IllegalArgumentException("The last service reading must be between 0 and the current reading.");
        if (interval == null || interval.signum() <= 0) throw new IllegalArgumentException("Give a service interval in " + word(newType) + " (more than zero).");
        return EarthmovingRules.requireReason(reason, "changing the meter");
    }

    public static final int MIN_DISPUTE_REASON = 5;

    /** A reading reduced to what billing needs. */
    public record Point(UUID id, String meterType, BigDecimal reading, Instant recordedAt, UUID correctsId) {}

    /** The reading that stood at a moment: its id and figure. */
    public record Standing(UUID id, BigDecimal reading) {}

    /**
     * The machine's reading at a moment, after corrections. A correction takes the place, in time, of the reading it
     * replaces, so a period that ended before the correction was made still sees the corrected figure. Only readings of the
     * given meter type count. Null when nothing was recorded before that moment.
     */
    public static Standing standingBefore(List<Point> all, String meterType, Instant before) {
        Map<UUID, Point> byId = new HashMap<>();
        Set<UUID> replaced = new HashSet<>();
        for (Point p : all) {
            byId.put(p.id(), p);
            if (p.correctsId() != null) replaced.add(p.correctsId());
        }
        Point best = null;
        Instant bestAt = null;
        for (Point p : all) {
            if (!meterType.equals(p.meterType()) || replaced.contains(p.id())) continue;
            Instant at = placeInTime(p, byId);
            if (!at.isBefore(before)) continue;
            if (best == null || at.isAfter(bestAt) || (at.equals(bestAt) && p.recordedAt().isAfter(best.recordedAt()))) { best = p; bestAt = at; }
        }
        return best == null ? null : new Standing(best.id(), best.reading());
    }

    private static Instant placeInTime(Point p, Map<UUID, Point> byId) {
        Point cur = p;
        for (int i = 0; i < 10 && cur.correctsId() != null && byId.containsKey(cur.correctsId()); i++) cur = byId.get(cur.correctsId());
        return cur.recordedAt();
    }

    /** A dispute needs a reason that says something. */
    public static String checkDispute(String reason) {
        if (reason == null || reason.trim().length() < MIN_DISPUTE_REASON)
            throw new IllegalArgumentException("Say what is disputed about this reading (at least " + MIN_DISPUTE_REASON + " characters).");
        String r = reason.trim();
        if (r.length() > 500) throw new IllegalArgumentException("Keep the reason under 500 characters.");
        return r;
    }

    /** Confirming a reading as right ends the dispute; the note says why it stands. */
    public static String checkResolution(String note) {
        if (note == null || note.trim().length() < MIN_DISPUTE_REASON)
            throw new IllegalArgumentException("Say why the reading stands (at least " + MIN_DISPUTE_REASON + " characters).");
        String n = note.trim();
        if (n.length() > 500) throw new IllegalArgumentException("Keep the note under 500 characters.");
        return n;
    }
}

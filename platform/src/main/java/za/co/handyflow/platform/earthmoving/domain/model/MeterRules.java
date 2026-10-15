package za.co.handyflow.platform.earthmoving.domain.model;

import java.math.BigDecimal;
import java.util.List;

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
}

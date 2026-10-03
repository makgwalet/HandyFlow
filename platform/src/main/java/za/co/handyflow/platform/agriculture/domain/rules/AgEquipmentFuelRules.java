package za.co.handyflow.platform.agriculture.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Equipment and fuel costing rules (ADR-001, W4).
 * <p>
 * Equipment: the hours a machine worked on a farm activity x its operating rate per hour, which Fleet defines as service and repairs ONLY.
 * Fuel: litres dispensed x the tank's cost per litre at the time, which Fuel snapshotted on the dispatch. Keeping the two rates apart is what
 * stops fuel being counted twice (once in an hourly rate, once as litres). Both rates are SNAPSHOTTED into the cost ledger.
 * <p>
 * Pure and dependency-free so the arithmetic is unit tested without Spring or a database.
 */
public final class AgEquipmentFuelRules {

    private AgEquipmentFuelRules() {}

    /** The cost ledger's source types. {@code sourceRef} is the vehicle (equipment) or the fuel dispatch (fuel). */
    public static final String EQUIPMENT_SOURCE = "FLEET_USAGE";
    public static final String FUEL_SOURCE = "FUEL_DISPATCH";

    /** A machine cannot work more than a day in a day; a bigger figure is a typing mistake. */
    public static final BigDecimal MAX_HOURS_PER_USE = new BigDecimal("24");

    /** @throws IllegalArgumentException unless the hours are above 0 and at most {@link #MAX_HOURS_PER_USE} */
    public static void requireHoursOfUse(BigDecimal hours) {
        if (hours == null || hours.signum() <= 0) throw new IllegalArgumentException("hours must be above zero");
        if (hours.compareTo(MAX_HOURS_PER_USE) > 0) throw new IllegalArgumentException("one use cannot be more than " + MAX_HOURS_PER_USE.toPlainString() + " hours; enter a separate use for each day");
    }

    /** The rate as it is stored in the ledger, to four places. */
    public static BigDecimal snapshotRate(BigDecimal rate) {
        return rate.setScale(4, RoundingMode.HALF_UP);
    }

    /** What the hours of use cost at the snapshotted operating rate, to the cent. */
    public static BigDecimal equipmentAmount(BigDecimal hours, BigDecimal snapshottedRatePerHour) {
        return hours.multiply(snapshottedRatePerHour).setScale(2, RoundingMode.HALF_UP);
    }

    /** What the litres cost at the snapshotted cost per litre, to the cent. */
    public static BigDecimal fuelAmount(BigDecimal litres, BigDecimal snapshottedCostPerLitre) {
        return litres.multiply(snapshottedCostPerLitre).setScale(2, RoundingMode.HALF_UP);
    }
}

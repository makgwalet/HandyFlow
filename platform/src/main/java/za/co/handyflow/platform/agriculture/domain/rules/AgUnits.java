package za.co.handyflow.platform.agriculture.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Unit-of-measure handling for harvest quantities.
 * <p>
 * WHY this exists: crop yield is the SUM of every harvest record's quantity, labelled with the crop type's default
 * unit. Units are free text, so one record in tonnes and another in kilograms used to add together as if they were
 * the same thing and silently produce a wrong yield (and a wrong yield per hectare). Mass units are therefore
 * converted to the crop's own unit before summing, and units that cannot be converted (bags, bales, crates...) are only
 * accepted when they already match the crop's unit.
 * <p>
 * Pure and dependency-free so the rules can be unit tested without Spring or a database.
 * {@code t} is the metric tonne (1000 kg), which is what "ton" and "tons" mean in South African farming.
 */
public final class AgUnits {

    private AgUnits() {}

    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("kg", "kg"), Map.entry("kgs", "kg"), Map.entry("kilogram", "kg"), Map.entry("kilograms", "kg"),
            Map.entry("kilo", "kg"), Map.entry("kilos", "kg"),
            Map.entry("g", "g"), Map.entry("gram", "g"), Map.entry("grams", "g"),
            Map.entry("t", "t"), Map.entry("ton", "t"), Map.entry("tons", "t"), Map.entry("tonne", "t"), Map.entry("tonnes", "t"),
            Map.entry("lb", "lb"), Map.entry("lbs", "lb"), Map.entry("pound", "lb"), Map.entry("pounds", "lb"));

    /** Kilograms in one of each mass unit. */
    private static final Map<String, BigDecimal> KG_PER_UNIT = Map.of(
            "kg", BigDecimal.ONE,
            "g", new BigDecimal("0.001"),
            "t", new BigDecimal("1000"),
            "lb", new BigDecimal("0.45359237"));

    /** Trimmed, lower-cased, with known aliases folded ("Tonnes" and " T " both become "t"). Unknown units are only normalised for case and spacing. */
    public static String canonical(String unit) {
        if (unit == null) return "";
        String key = unit.trim().toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(key, key);
    }

    public static boolean isMass(String unit) {
        return KG_PER_UNIT.containsKey(canonical(unit));
    }

    /** True when both units are present and mean the same thing. */
    public static boolean sameUnit(String a, String b) {
        String ca = canonical(a);
        return !ca.isEmpty() && ca.equals(canonical(b));
    }

    /** True when a quantity in {@code from} can be expressed in {@code to}: the same unit, or two mass units. */
    public static boolean canConvert(String from, String to) {
        return sameUnit(from, to) || (isMass(from) && isMass(to));
    }

    /** The quantity expressed in {@code to}, or empty when the units are incompatible or missing. */
    public static Optional<BigDecimal> convert(BigDecimal quantity, String from, String to) {
        if (quantity == null) return Optional.empty();
        String f = canonical(from);
        String t = canonical(to);
        if (f.isEmpty() || t.isEmpty()) return Optional.empty();
        if (f.equals(t)) return Optional.of(quantity);
        BigDecimal fromFactor = KG_PER_UNIT.get(f);
        BigDecimal toFactor = KG_PER_UNIT.get(t);
        if (fromFactor == null || toFactor == null) return Optional.empty();
        return Optional.of(quantity.multiply(fromFactor).divide(toFactor, 6, RoundingMode.HALF_UP));
    }

    /**
     * Rejects a harvest unit that can never be added to the crop's yield. Mass units convert to each other (kg, t, g, lb);
     * anything else (bags, bales, crates) must match the crop's own unit exactly. A crop with no unit configured accepts anything.
     */
    public static void requireConvertible(String enteredUnit, String cropUnit) {
        if (cropUnit == null || cropUnit.isBlank()) return;
        if (!canConvert(enteredUnit, cropUnit)) {
            String hint = isMass(cropUnit) ? "use a mass unit such as kg or t" : "use '" + cropUnit.trim() + "'";
            throw new IllegalArgumentException("harvest unit '" + (enteredUnit == null ? "" : enteredUnit.trim())
                    + "' cannot be converted to this crop's yield unit '" + cropUnit.trim() + "': " + hint);
        }
    }

    /** One group of harvest records that share a unit. */
    public record UnitQuantity(String unit, BigDecimal quantity) {}

    /**
     * @param total            the yield expressed in the crop's unit
     * @param unconvertedUnits how many distinct units could not be converted and were left out of {@code total}
     *                         (only possible for records saved before units were validated)
     */
    public record YieldTotal(BigDecimal total, int unconvertedUnits) {}

    /**
     * Sums harvest quantities in the crop's own unit. Quantities in a convertible unit are converted; the rest are excluded
     * and counted so the report can say the figure is incomplete, instead of silently adding kilograms to bags.
     * With no target unit (the crop type could not be found) nothing can be converted, so quantities are summed as recorded
     * and a mix of units is still reported.
     */
    public static YieldTotal sumInto(String targetUnit, List<UnitQuantity> rows) {
        BigDecimal total = BigDecimal.ZERO;
        Set<String> unconverted = new HashSet<>();
        Set<String> distinct = new HashSet<>();
        for (UnitQuantity r : rows) {
            if (r.quantity() == null) continue;
            distinct.add(canonical(r.unit()));
            if (targetUnit == null || targetUnit.isBlank()) {
                total = total.add(r.quantity());
                continue;
            }
            Optional<BigDecimal> converted = convert(r.quantity(), r.unit(), targetUnit);
            if (converted.isPresent()) total = total.add(converted.get());
            else unconverted.add(canonical(r.unit()));
        }
        int flagged = (targetUnit == null || targetUnit.isBlank()) ? Math.max(0, distinct.size() - 1) : unconverted.size();
        return new YieldTotal(total, flagged);
    }
}

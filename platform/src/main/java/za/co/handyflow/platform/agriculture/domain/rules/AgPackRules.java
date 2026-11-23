package za.co.handyflow.platform.agriculture.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

/**
 * Pack size ("kg per bag") for stock counted in bags, bales or sacks. Stock stays in the item's own unit; the pack size only
 * converts kilograms to and from it. It applies to count units only: a kg or tonne item needs none.
 */
public final class AgPackRules {

    private AgPackRules() {}

    public static final BigDecimal MAX_PACK_KG = new BigDecimal("10000");
    private static final Set<String> COUNT_UNITS = Set.of("bag", "bags", "bale", "bales", "sack", "sacks");

    public static boolean isCountUnit(String unit) {
        return unit != null && COUNT_UNITS.contains(unit.trim().toLowerCase(Locale.ROOT));
    }

    /** "bags" becomes "bag", for messages. Anything else is returned trimmed. */
    public static String singular(String unit) {
        if (unit == null) return "";
        String u = unit.trim().toLowerCase(Locale.ROOT);
        return COUNT_UNITS.contains(u) && u.endsWith("s") ? u.substring(0, u.length() - 1) : u;
    }

    /** Null (no pack size) is allowed. A value needs a count unit, more than zero and at most 10 000 kg. Returns it at 3 decimals. */
    public static BigDecimal requirePackSize(String unit, BigDecimal packKg) {
        if (packKg == null) return null;
        if (!isCountUnit(unit))
            throw new IllegalArgumentException("Kg per bag only applies to items counted in bags, bales or sacks");
        if (packKg.signum() <= 0) throw new IllegalArgumentException("Kg per " + singular(unit) + " must be more than zero");
        if (packKg.compareTo(MAX_PACK_KG) > 0) throw new IllegalArgumentException("Kg per " + singular(unit) + " is not credible. Check the number");
        return packKg.setScale(3, RoundingMode.HALF_UP);
    }

    /** Kilograms expressed in whole-and-part bags. */
    public static BigDecimal kgToUnits(BigDecimal kg, BigDecimal packKg) {
        return kg.divide(packKg, 3, RoundingMode.HALF_UP);
    }

    public static BigDecimal unitsToKg(BigDecimal units, BigDecimal packKg) {
        return units.multiply(packKg).setScale(3, RoundingMode.HALF_UP);
    }

    /** Price per bag divided by kg per bag, at 4 decimals. Null when either is missing. */
    public static BigDecimal costPerKg(BigDecimal pricePerUnit, BigDecimal packKg) {
        if (pricePerUnit == null || packKg == null || packKg.signum() <= 0) return null;
        return pricePerUnit.divide(packKg, 4, RoundingMode.HALF_UP);
    }
}

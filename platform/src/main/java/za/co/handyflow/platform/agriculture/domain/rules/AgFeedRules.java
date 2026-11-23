package za.co.handyflow.platform.agriculture.domain.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Feed drawn from stock. Before these rules the kilograms fed were taken off the stock item as if the item were counted in kg
 * (a tonne-counted item lost 1000 times too much), the item could belong to a different farm, a deactivated item could be used,
 * and the cost per kg was whatever was typed even though the item's own cost was known.
 */
public final class AgFeedRules {

    private AgFeedRules() {}

    public static void requireStockItem(UUID itemFarmId, String itemStatus, UUID targetFarmId, String itemName) {
        if (itemFarmId == null || !itemFarmId.equals(targetFarmId))
            throw new IllegalArgumentException("'" + itemName + "' belongs to a different farm");
        if (!"ACTIVE".equals(itemStatus))
            throw new IllegalArgumentException("'" + itemName + "' is inactive and cannot be fed from");
    }

    public static void requirePositive(BigDecimal quantityKg) {
        if (quantityKg == null || quantityKg.signum() <= 0) throw new IllegalArgumentException("Quantity fed must be more than zero");
    }

    /** The kilograms fed, expressed in the stock item's own unit (kg, t, g, lb). Bags, bales and the like cannot be converted. */
    public static BigDecimal quantityInItemUnit(BigDecimal quantityKg, String itemUnit, String itemName) {
        return AgUnits.convert(quantityKg, "kg", itemUnit).orElseThrow(() -> new IllegalArgumentException(
                "'" + itemName + "' is counted in '" + (itemUnit == null ? "" : itemUnit.trim())
                        + "', so kilograms fed cannot be taken off it. Change the item's unit to kg or t to feed from it."));
    }

    /** The cost per kg: what was typed if anything, otherwise the item's unit cost converted to kg. Null if neither is known. */
    public static BigDecimal costPerKg(BigDecimal typed, BigDecimal itemUnitCost, String itemUnit) {
        if (typed != null) return typed;
        if (itemUnitCost == null) return null;
        BigDecimal unitsPerKg = AgUnits.convert(BigDecimal.ONE, "kg", itemUnit).orElse(null);
        if (unitsPerKg == null) return null;
        return itemUnitCost.multiply(unitsPerKg).setScale(4, RoundingMode.HALF_UP);
    }
}

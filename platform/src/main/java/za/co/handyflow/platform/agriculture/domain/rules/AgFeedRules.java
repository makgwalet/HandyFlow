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

    /** As above, but a bag, bale or sack item converts through its kg per bag. Without one, feeding from it is blocked with a prompt. */
    public static BigDecimal quantityInItemUnit(BigDecimal quantityKg, String itemUnit, String itemName, BigDecimal packSizeKg) {
        if (AgPackRules.isCountUnit(itemUnit)) {
            if (packSizeKg == null) throw new IllegalArgumentException(
                    "'" + itemName + "' is counted in '" + itemUnit.trim() + "', so kilograms fed cannot be taken off it. Set kg per "
                            + AgPackRules.singular(itemUnit) + " on the item to feed from it.");
            return AgPackRules.kgToUnits(quantityKg, packSizeKg);
        }
        return quantityInItemUnit(quantityKg, itemUnit, itemName);
    }

    /** The kilograms fed, from what was entered: kilograms, or bags of a stock item with a pack size (never both, never neither). */
    public static BigDecimal resolveQuantityKg(BigDecimal kg, BigDecimal bags, boolean fromStock, String itemName, String itemUnit, BigDecimal packSizeKg) {
        if (kg != null && bags != null) throw new IllegalArgumentException("Give the amount fed in kg or in bags, not both");
        if (kg == null && bags == null) throw new IllegalArgumentException("Enter the amount fed in kg or in bags");
        if (bags == null) return kg;
        if (!fromStock) throw new IllegalArgumentException("Feed can be entered in bags only when it comes from a stock item");
        if (!AgPackRules.isCountUnit(itemUnit)) throw new IllegalArgumentException("'" + itemName + "' is not counted in bags, bales or sacks");
        if (packSizeKg == null) throw new IllegalArgumentException("'" + itemName + "' has no kg per " + AgPackRules.singular(itemUnit) + ". Set it on the item first");
        if (bags.signum() <= 0) throw new IllegalArgumentException("Quantity fed must be more than zero");
        return AgPackRules.unitsToKg(bags, packSizeKg);
    }

    /** As the three-argument version, but a bag item's price per bag is divided by its kg per bag. */
    public static BigDecimal costPerKg(BigDecimal typed, BigDecimal itemUnitCost, String itemUnit, BigDecimal packSizeKg) {
        if (typed != null) return typed;
        if (AgPackRules.isCountUnit(itemUnit)) return AgPackRules.costPerKg(itemUnitCost, packSizeKg);
        return costPerKg(typed, itemUnitCost, itemUnit);
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

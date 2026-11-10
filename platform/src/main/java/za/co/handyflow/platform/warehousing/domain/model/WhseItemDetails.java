package za.co.handyflow.platform.warehousing.domain.model;

import java.math.BigDecimal;
import java.util.Set;

/** Optional item-master data: barcode, size, weight, cost, stock levels and handling flags. Validated on construction. */
public record WhseItemDetails(
        String barcode, BigDecimal lengthMm, BigDecimal widthMm, BigDecimal heightMm, BigDecimal weightKg, BigDecimal unitCost,
        BigDecimal minQty, BigDecimal maxQty, String temperatureClass, boolean hazardous, boolean fragile, boolean stackable,
        String unitCostCurrency) {

    public static final String DEFAULT_CURRENCY = "ZAR";

    /** Cost in rands, the currency every item had before the currency was recorded. */
    public WhseItemDetails(String barcode, BigDecimal lengthMm, BigDecimal widthMm, BigDecimal heightMm, BigDecimal weightKg, BigDecimal unitCost,
                           BigDecimal minQty, BigDecimal maxQty, String temperatureClass, boolean hazardous, boolean fragile, boolean stackable) {
        this(barcode, lengthMm, widthMm, heightMm, weightKg, unitCost, minQty, maxQty, temperatureClass, hazardous, fragile, stackable, DEFAULT_CURRENCY);
    }


    public static final Set<String> TEMPERATURE_CLASSES = Set.of("AMBIENT", "CHILLED", "FROZEN", "CONTROLLED_ROOM");

    public WhseItemDetails {
        barcode = Gtin.normalise(barcode);
        positive(lengthMm, "length");
        positive(widthMm, "width");
        positive(heightMm, "height");
        positive(weightKg, "weight");
        unitCostCurrency = unitCostCurrency == null || unitCostCurrency.isBlank() ? DEFAULT_CURRENCY : unitCostCurrency.trim().toUpperCase();
        if (!unitCostCurrency.matches("[A-Z]{3}")) throw new IllegalArgumentException("The cost currency must be a 3-letter code such as ZAR or USD.");
        if (unitCost != null && unitCost.signum() < 0) throw new IllegalArgumentException("Unit cost cannot be negative.");
        if (minQty != null && minQty.signum() < 0) throw new IllegalArgumentException("Minimum level cannot be negative.");
        if (maxQty != null && maxQty.signum() < 0) throw new IllegalArgumentException("Maximum level cannot be negative.");
        if (minQty != null && maxQty != null && maxQty.compareTo(minQty) < 0) {
            throw new IllegalArgumentException("The maximum level cannot be lower than the minimum level.");
        }
        temperatureClass = temperatureClass == null || temperatureClass.isBlank() ? "AMBIENT" : temperatureClass.trim().toUpperCase();
        if (!TEMPERATURE_CLASSES.contains(temperatureClass)) {
            throw new IllegalArgumentException("Temperature class must be one of " + TEMPERATURE_CLASSES + ".");
        }
    }

    public static WhseItemDetails none() {
        return new WhseItemDetails(null, null, null, null, null, null, null, null, "AMBIENT", false, false, true);
    }

    private static void positive(BigDecimal v, String what) {
        if (v != null && v.signum() <= 0) throw new IllegalArgumentException("The " + what + " must be greater than zero.");
    }

    /** Cubic metres, or null unless all three dimensions are known. */
    public BigDecimal volumeM3() {
        if (lengthMm == null || widthMm == null || heightMm == null) return null;
        return lengthMm.multiply(widthMm).multiply(heightMm).divide(new BigDecimal("1000000000"), 6, java.math.RoundingMode.HALF_UP);
    }
}

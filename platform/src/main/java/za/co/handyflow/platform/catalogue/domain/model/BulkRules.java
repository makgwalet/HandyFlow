package za.co.handyflow.platform.catalogue.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The arithmetic and limits of changing many items at once. */
public final class BulkRules {
    private BulkRules() {}

    public static final int MAX_ITEMS = 200;
    public static final BigDecimal MIN_PERCENT = new BigDecimal("-90"), MAX_PERCENT = new BigDecimal("500");

    /** A percentage change must be given, not zero, and between -90 and +500. */
    public static BigDecimal checkPercent(BigDecimal percent) {
        if (percent == null) throw new IllegalArgumentException("Enter the percentage to change prices by, for example 5 or -10.");
        if (percent.signum() == 0) throw new IllegalArgumentException("A change of 0% changes nothing.");
        if (percent.compareTo(MIN_PERCENT) < 0 || percent.compareTo(MAX_PERCENT) > 0)
            throw new IllegalArgumentException("The percentage must be between " + MIN_PERCENT.toPlainString() + " and " + MAX_PERCENT.toPlainString() + ".");
        return percent;
    }

    /** The old price changed by the percentage, rounded to the cent (half up). */
    public static BigDecimal newPrice(BigDecimal oldPrice, BigDecimal percent) {
        return oldPrice.multiply(BigDecimal.ONE.add(percent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))).setScale(2, RoundingMode.HALF_UP);
    }

    public static void checkCount(int n) {
        if (n == 0) throw new IllegalArgumentException("Choose at least one item.");
        if (n > MAX_ITEMS) throw new IllegalArgumentException("Choose at most " + MAX_ITEMS + " items at a time.");
    }
}

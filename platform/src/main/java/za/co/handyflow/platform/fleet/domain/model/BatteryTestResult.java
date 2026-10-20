package za.co.handyflow.platform.fleet.domain.model;

import java.math.BigDecimal;

/**
 * Result of a resting-voltage test, scaled to a 12 V reference: 12.4 V or more is GOOD,
 * 12.0–12.4 V WEAK, below 12.0 V FAILED. A 24 V battery is judged at twice those figures.
 * (A voltage reading is only meaningful with the battery at rest and the engine off.)
 */
public enum BatteryTestResult {
    GOOD, WEAK, FAILED;

    private static final BigDecimal GOOD_AT = new BigDecimal("12.4");
    private static final BigDecimal WEAK_AT = new BigDecimal("12.0");

    public static BatteryTestResult classify(BigDecimal voltage, int nominalVoltage) {
        BigDecimal per12 = voltage.multiply(BigDecimal.valueOf(12)).divide(BigDecimal.valueOf(nominalVoltage), 3,
                java.math.RoundingMode.HALF_UP);
        if (per12.compareTo(GOOD_AT) >= 0) return GOOD;
        if (per12.compareTo(WEAK_AT) >= 0) return WEAK;
        return FAILED;
    }
}
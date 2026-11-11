package za.co.handyflow.platform.fuel.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** One pump's meter readings within a shift. The price is copied at open so a later price change cannot rewrite history. */
@Entity
@Table(name = "fuel_shift_readings")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class FuelShiftReading {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "shift_id", nullable = false, updatable = false) private UUID shiftId;
    @Column(name = "pump_id", nullable = false, updatable = false) private UUID pumpId;
    @Column(name = "tank_id", nullable = false, updatable = false) private UUID tankId;
    @Column(name = "fuel_type", nullable = false, updatable = false, length = 30) private String fuelType;
    @Column(name = "opening_meter", nullable = false, updatable = false, precision = 14, scale = 2) private BigDecimal openingMeter;
    @Column(name = "opening_gap", nullable = false, updatable = false, precision = 14, scale = 2) private BigDecimal openingGap = BigDecimal.ZERO;
    @Column(name = "closing_meter", precision = 14, scale = 2) private BigDecimal closingMeter;
    @Column(name = "test_litres", nullable = false, precision = 14, scale = 2) private BigDecimal testLitres = BigDecimal.ZERO;
    @Column(name = "litres_sold", precision = 14, scale = 2) private BigDecimal litresSold;
    @Column(name = "price_per_litre", nullable = false, updatable = false, precision = 10, scale = 4) private BigDecimal pricePerLitre;
    @Column(name = "sales_value", precision = 14, scale = 2) private BigDecimal salesValue;
    @Column(name = "cost_value", precision = 14, scale = 2) private BigDecimal costValue;
    @Column(name = "is_open", nullable = false) private boolean open = true;

    public static FuelShiftReading open(TenantId tenantId, UUID shiftId, FuelPump pump, String fuelType, BigDecimal openingMeter, BigDecimal gap) {
        if (openingMeter == null || openingMeter.signum() < 0) throw new IllegalArgumentException("Enter the opening meter for " + pump.getName() + ".");
        FuelShiftReading r = new FuelShiftReading();
        r.tenantId = tenantId;
        r.shiftId = shiftId;
        r.pumpId = pump.getId();
        r.tankId = pump.getTankId();
        r.fuelType = fuelType;
        r.openingMeter = openingMeter;
        r.openingGap = gap == null ? BigDecimal.ZERO : gap;
        r.pricePerLitre = pump.getPricePerLitre();
        return r;
    }

    /**
     * Closes the reading. Litres sold = meter movement minus test litres (fuel pumped back into the tank for calibration).
     * @param costPerLitre the tank's weighted-average cost now, or null if unknown
     */
    public void close(BigDecimal closing, BigDecimal test, BigDecimal costPerLitre, String pumpName) {
        if (closing == null) throw new IllegalArgumentException("Enter the closing meter for " + pumpName + ".");
        if (closing.compareTo(openingMeter) < 0) {
            throw new IllegalArgumentException("The closing meter for " + pumpName + " (" + closing.toPlainString() + ") is below the opening meter (" + openingMeter.toPlainString() + ").");
        }
        BigDecimal t = test == null ? BigDecimal.ZERO : test;
        if (t.signum() < 0) throw new IllegalArgumentException("Test litres cannot be negative.");
        BigDecimal moved = closing.subtract(openingMeter);
        if (t.compareTo(moved) > 0) throw new IllegalArgumentException("Test litres for " + pumpName + " are more than the meter moved.");
        this.closingMeter = closing;
        this.testLitres = t;
        this.litresSold = moved.subtract(t);
        this.salesValue = litresSold.multiply(pricePerLitre).setScale(2, RoundingMode.HALF_UP);
        this.costValue = costPerLitre == null ? null : litresSold.multiply(costPerLitre).setScale(2, RoundingMode.HALF_UP);
        this.open = false;
    }

    /** A reversed shift frees nothing on the pump (the meter stays where it was), but the reading no longer holds the pump open. */
    public boolean isClosed() { return closingMeter != null; }
}

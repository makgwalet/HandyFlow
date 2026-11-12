package za.co.handyflow.platform.fuel.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.fuel.domain.model.FuelReceipt;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 7b: freight is added to the tank's cost, while the supplier's price and the receipt total stay as charged. */
class FuelReceiptFreightTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    private FuelReceipt receipt(String litres, String price) {
        return FuelReceipt.create(T, UUID.randomUUID(), null, bd(litres), bd(price), Instant.now(), null, null, bd("0"), bd(litres));
    }

    @Test @DisplayName("no freight, or zero freight, leaves the supplier's price exactly as it was")
    void noFreight() {
        assertEquals(0, bd("20.0000").compareTo(FuelReceipt.landedPrice(bd("20.0000"), bd("5000"), null)));
        assertEquals(0, bd("20.0000").compareTo(FuelReceipt.landedPrice(bd("20.0000"), bd("5000"), BigDecimal.ZERO)));
    }

    @Test @DisplayName("freight is spread over the litres and added to the price")
    void spreadOverLitres() {
        // R1,500 freight over 5,000 L is R0.30 a litre.
        assertEquals(0, bd("20.3000").compareTo(FuelReceipt.landedPrice(bd("20.0000"), bd("5000"), bd("1500"))));
        // an uneven split rounds to four places
        assertEquals(0, bd("20.3333").compareTo(FuelReceipt.landedPrice(bd("20.0000"), bd("3000"), bd("1000"))));
    }

    @Test @DisplayName("negative freight is refused")
    void negativeRefused() {
        assertThrows(IllegalArgumentException.class, () -> FuelReceipt.landedPrice(bd("20"), bd("100"), bd("-1")));
        assertThrows(IllegalArgumentException.class, () -> receipt("100", "20").recordFreight(bd("-5"), null));
    }

    @Test @DisplayName("the receipt keeps the supplier's price and total cost; freight sits beside them")
    void receiptKeepsSupplierFigures() {
        FuelReceipt r = receipt("5000", "20");
        r.recordFreight(bd("1500"), "  Carrier invoice C-77  ");
        assertEquals(0, bd("20").compareTo(r.getPricePerLitre()));
        assertEquals(0, bd("100000.00").compareTo(r.getTotalCost()));        // supplier cost only, so invoice matching is unaffected
        assertEquals(0, bd("1500.00").compareTo(r.getFreightAmount()));
        assertEquals("Carrier invoice C-77", r.getFreightNote());
        assertEquals(0, bd("20.3000").compareTo(r.landedPricePerLitre()));
    }

    @Test @DisplayName("zero or blank freight records nothing, and a note without freight is dropped")
    void zeroFreightRecordsNothing() {
        FuelReceipt r = receipt("100", "20");
        r.recordFreight(BigDecimal.ZERO, "x");
        assertNull(r.getFreightAmount()); assertNull(r.getFreightNote());
        r.recordFreight(null, "x");
        assertNull(r.getFreightAmount()); assertNull(r.getFreightNote());
        assertEquals(0, bd("20").compareTo(r.landedPricePerLitre()));
    }

    @Test @DisplayName("the tank's average cost includes the freight: 5,000 L at R20 plus 5,000 L at R21 with R1,500 freight")
    void tankCostIncludesFreight() {
        FuelTank tank = FuelTank.create(T, "Diesel 1", "DIESEL", bd("20000"), "Yard", null);
        tank.addStock(bd("5000"), bd("20.00"));
        tank.addStock(bd("5000"), FuelReceipt.landedPrice(bd("21.00"), bd("5000"), bd("1500")));   // R21.30 landed
        // (5000 x 20 + 5000 x 21.30) / 10000 = 20.65
        assertEquals(0, bd("20.65").compareTo(tank.getCostPerLitreWac().setScale(2, java.math.RoundingMode.HALF_UP)));
    }

    @Test @DisplayName("a long note is cut to 200 characters")
    void noteCut() {
        FuelReceipt r = receipt("100", "20");
        r.recordFreight(bd("10"), "x".repeat(300));
        assertEquals(200, r.getFreightNote().length());
    }
}

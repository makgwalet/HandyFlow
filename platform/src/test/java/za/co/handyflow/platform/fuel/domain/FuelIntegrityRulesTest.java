package za.co.handyflow.platform.fuel.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.fuel.domain.model.FuelDispatch;
import za.co.handyflow.platform.fuel.domain.model.FuelReceipt;
import za.co.handyflow.platform.fuel.domain.model.FuelTank;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Stage 0 integrity rules: pure domain, no Spring. */
class FuelIntegrityRulesTest {

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private static BigDecimal bd(String s) { return new BigDecimal(s); }

    private FuelTank tank(String capacity, String level) {
        FuelTank t = FuelTank.create(TENANT, "Diesel 1", "DIESEL", bd(capacity), "Yard", null);
        if (bd(level).signum() > 0) t.addStock(bd(level), bd("20.00"));
        return t;
    }

    private FuelDelivery delivery() {
        return FuelDelivery.create(TENANT, UUID.randomUUID(), null, Map.of(), "DIESEL",
                bd("1000"), bd("22.50"), Instant.now(), null, null);
    }

    private void complete(FuelDelivery d) {
        d.complete(bd("1000"), "P Botha", null, null, null, false, null, null);
    }

    // ── Tank quantities ──────────────────────────────────────────────────────

    @Test @DisplayName("receiving zero or negative litres is refused")
    void addStockRejectsNonPositive() {
        FuelTank t = tank("5000", "0");
        assertThrows(IllegalArgumentException.class, () -> t.addStock(bd("0"), bd("20")));
        assertThrows(IllegalArgumentException.class, () -> t.addStock(bd("-100"), bd("20")));
        assertEquals(0, t.getCurrentLitres().signum());
    }

    @Test @DisplayName("a negative dispense can no longer ADD fuel")
    void removeStockRejectsNonPositive() {
        FuelTank t = tank("5000", "1000");
        assertThrows(IllegalArgumentException.class, () -> t.removeStock(bd("-500")));
        assertThrows(IllegalArgumentException.class, () -> t.removeStock(bd("0")));
        assertEquals(0, bd("1000").compareTo(t.getCurrentLitres()));
    }

    @Test @DisplayName("dispensing more than is in the tank is refused")
    void removeStockRejectsOverdraw() {
        FuelTank t = tank("5000", "1000");
        assertThrows(IllegalArgumentException.class, () -> t.removeStock(bd("1000.01")));
    }

    @Test @DisplayName("restoreStock puts litres back, respects capacity, leaves WAC alone")
    void restoreStock() {
        FuelTank t = tank("2000", "1500");
        BigDecimal wac = t.getCostPerLitreWac();
        t.removeStock(bd("500"));
        t.restoreStock(bd("500"));
        assertEquals(0, bd("1500").compareTo(t.getCurrentLitres()));
        assertEquals(0, wac.compareTo(t.getCostPerLitreWac()));
        assertThrows(IllegalStateException.class, () -> t.restoreStock(bd("600")));
    }

    // ── Delivery state machine ───────────────────────────────────────────────

    @Test @DisplayName("a delivery can be completed once, not twice")
    void completeOnce() {
        FuelDelivery d = delivery();
        complete(d);
        assertEquals("DELIVERED", d.getStatus());
        assertThrows(IllegalStateException.class, () -> complete(d));
    }

    @Test @DisplayName("a cancelled delivery cannot be completed")
    void cancelledCannotComplete() {
        FuelDelivery d = delivery();
        d.cancel();
        assertThrows(IllegalStateException.class, () -> complete(d));
    }

    @Test @DisplayName("a delivered delivery cannot be cancelled or dispatched")
    void deliveredIsFinal() {
        FuelDelivery d = delivery();
        complete(d);
        assertThrows(IllegalStateException.class, d::cancel);
        assertThrows(IllegalStateException.class, () -> d.dispatch("Sipho", "CA 123"));
    }

    @Test @DisplayName("SCHEDULED to IN_TRANSIT to DELIVERED is allowed")
    void happyPath() {
        FuelDelivery d = delivery();
        d.dispatch("Sipho", "CA 123");
        assertEquals("IN_TRANSIT", d.getStatus());
        assertThrows(IllegalStateException.class, () -> d.dispatch("Sipho", "CA 123"));
        complete(d);
        assertEquals("DELIVERED", d.getStatus());
    }

    @Test @DisplayName("only a DELIVERED delivery can be reversed, and only once")
    void reverseDelivery() {
        FuelDelivery d = delivery();
        assertThrows(IllegalStateException.class, () -> d.reverse(UUID.randomUUID(), "Wrong tank"));
        complete(d);
        UUID user = UUID.randomUUID();
        d.reverse(user, "Captured against the wrong tank");
        assertEquals("REVERSED", d.getStatus());
        assertEquals(user, d.getReversedBy());
        assertNotNull(d.getReversedAt());
        assertThrows(IllegalStateException.class, () -> d.reverse(user, "Again"));
    }

    // ── Receipt / dispatch reversal ──────────────────────────────────────────

    @Test @DisplayName("a receipt can be reversed once")
    void reverseReceiptOnce() {
        FuelReceipt r = FuelReceipt.create(TENANT, UUID.randomUUID(), null, bd("500"), bd("20"),
                Instant.now(), null, null, bd("0"), bd("500"));
        assertFalse(r.isReversed());
        r.reverse(UUID.randomUUID(), "Duplicate entry");
        assertTrue(r.isReversed());
        assertThrows(IllegalStateException.class, () -> r.reverse(UUID.randomUUID(), "Again"));
    }

    @Test @DisplayName("a dispatch can be reversed once")
    void reverseDispatchOnce() {
        FuelDispatch d = FuelDispatch.create(TENANT, UUID.randomUUID(), null, null, null, "Tractor",
                bd("100"), null, Instant.now(), null, null, null, null, bd("500"), bd("400"));
        d.reverse(UUID.randomUUID(), "Wrong tractor");
        assertTrue(d.isReversed());
        assertThrows(IllegalStateException.class, () -> d.reverse(UUID.randomUUID(), "Again"));
    }
}

package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.fuel.domain.model.FuelSalesOrder;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FuelSalesOrderTest {

    static final TenantId T = TenantId.of(UUID.randomUUID());
    static BigDecimal bd(String s) { return new BigDecimal(s); }

    FuelSalesOrder quote(String litres, LocalDate validUntil) {
        return FuelSalesOrder.create(T, "SO-2026-00001", UUID.randomUUID(), UUID.randomUUID(), null, "DIESEL",
                bd(litres), bd("22.50"), null, validUntil, null, null, null);
    }

    @Test @DisplayName("a new order is a quotation worth litres x price")
    void startsAsQuote() {
        FuelSalesOrder o = quote("30000", null);
        assertEquals("QUOTE", o.getStatus());
        assertEquals(0, bd("675000.00").compareTo(o.value()));
    }

    @Test @DisplayName("zero litres, negative price and missing fuel type are refused")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> quote("0", null));
        assertThrows(IllegalArgumentException.class, () -> quote("-5", null));
        assertThrows(IllegalArgumentException.class, () -> FuelSalesOrder.create(T, "X", UUID.randomUUID(), UUID.randomUUID(), null,
                "DIESEL", bd("10"), bd("-1"), null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> FuelSalesOrder.create(T, "X", UUID.randomUUID(), UUID.randomUUID(), null,
                " ", bd("10"), bd("1"), null, null, null, null, null));
    }

    @Test @DisplayName("an expired quotation cannot be confirmed; a confirmed order cannot be edited or confirmed again")
    void confirmRules() {
        FuelSalesOrder expired = quote("1000", LocalDate.now().minusDays(1));
        assertThrows(IllegalStateException.class, () -> expired.confirm(UUID.randomUUID(), null, LocalDate.now()));
        FuelSalesOrder o = quote("1000", LocalDate.now().plusDays(3));
        assertThrows(IllegalArgumentException.class, () -> o.confirm(null, null, LocalDate.now()));
        o.confirm(UUID.randomUUID(), null, LocalDate.now());
        assertEquals("CONFIRMED", o.getStatus());
        assertThrows(IllegalStateException.class, () -> o.confirm(UUID.randomUUID(), null, LocalDate.now()));
        assertThrows(IllegalStateException.class, () -> o.revise(null, "DIESEL", bd("5"), bd("1"), null, null, null, null));
    }

    @Test @DisplayName("status follows delivered litres, including after a reversal")
    void followsDelivered() {
        FuelSalesOrder o = quote("1000", null);
        o.confirm(UUID.randomUUID(), null, LocalDate.now());
        o.followDelivered(bd("400"));
        assertEquals("PART_DELIVERED", o.getStatus());
        o.followDelivered(bd("1000"));
        assertEquals("DELIVERED", o.getStatus());
        o.followDelivered(bd("600"));
        assertEquals("PART_DELIVERED", o.getStatus());
        o.followDelivered(BigDecimal.ZERO);
        assertEquals("CONFIRMED", o.getStatus());
    }

    @Test @DisplayName("a quotation or cancelled order never moves with deliveries")
    void inertStates() {
        FuelSalesOrder q = quote("1000", null);
        q.followDelivered(bd("1000"));
        assertEquals("QUOTE", q.getStatus());
        q.cancel("Customer withdrew");
        q.followDelivered(bd("1000"));
        assertEquals("CANCELLED", q.getStatus());
    }

    @Test @DisplayName("cancelling needs a reason and is refused once delivered or already cancelled")
    void cancelRules() {
        FuelSalesOrder o = quote("1000", null);
        assertThrows(IllegalArgumentException.class, () -> o.cancel("no"));
        o.cancel("Customer withdrew");
        assertThrows(IllegalStateException.class, () -> o.cancel("Again please"));
        FuelSalesOrder d = quote("1000", null);
        d.confirm(UUID.randomUUID(), null, LocalDate.now());
        d.followDelivered(bd("1000"));
        assertThrows(IllegalStateException.class, () -> d.cancel("Too late now"));
    }
}

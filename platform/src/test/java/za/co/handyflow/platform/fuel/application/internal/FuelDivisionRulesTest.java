package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.fuel.domain.model.FuelDivision;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.fuel.domain.model.FuelDivision.*;

class FuelDivisionRulesTest {

    private static Set<FuelDivision> req(String path) { return FuelDivisionRules.requirementFor("/api/v1/fuel" + path); }

    @Test @DisplayName("forecourt needs Retail; tankers, planning and driver loads need Distribution")
    void divisionOnly() {
        assertEquals(Set.of(RETAIL_FORECOURT), req("/forecourt/shifts/abc/close"));
        for (String p : new String[]{"/tankers", "/drivers", "/planning", "/driver/loads/1/complete"}) assertEquals(Set.of(DISTRIBUTION), req(p), p);
    }

    @Test @DisplayName("customers, orders, prices, invoicing and deliveries are shared by Wholesale and Distribution")
    void commercial() {
        for (String p : new String[]{"/customer-accounts/1/sites", "/customer-sites/2", "/orders/3/confirm", "/invoicing/queue", "/prices/resolve", "/deliveries/4/complete", "/customer-accounts/1/portal-access"})
            assertEquals(Set.of(WHOLESALE_SUPPLY, DISTRIBUTION), req(p), p);
    }

    @Test @DisplayName("purchase orders and supplier invoices are shared by Wholesale and Retail")
    void procurement() {
        assertEquals(Set.of(WHOLESALE_SUPPLY, RETAIL_FORECOURT), req("/purchase-orders/1/place"));
        assertEquals(Set.of(WHOLESALE_SUPPLY, RETAIL_FORECOURT), req("/supplier-invoices"));
    }

    @Test @DisplayName("shared foundation, setup and the customer portal are open to every Fuel tenant")
    void shared() {
        for (String p : new String[]{"/tanks", "/depots", "/tanks/1/receive", "/ledger", "/transfers", "/reservations", "/compliance", "/dashboard", "/dashboard/margin",
                "/settings", "/operating-profile", "/operating-profile/activate", "/legal-entities", "/depots/1/legal-entity", "/portal/accounts", "/suppliers", "/margin-report"})
            assertTrue(req(p).isEmpty(), p);
    }

    @Test @DisplayName("a path outside Fuel, or a path that only starts with a rule's name, is not matched")
    void notMatched() {
        assertTrue(FuelDivisionRules.requirementFor("/api/v1/hr/orders").isEmpty());
        assertTrue(FuelDivisionRules.requirementFor(null).isEmpty());
        assertTrue(req("/orders-archive").isEmpty());
        assertTrue(req("/forecourtx").isEmpty());
    }

    @Test @DisplayName("every division has a readable label")
    void labels() { for (FuelDivision d : FuelDivision.values()) assertFalse(FuelDivisionRules.label(d).isBlank()); }
}

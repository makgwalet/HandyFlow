package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pure entity-behaviour tests, no Spring context (same convention as AgCropCycleTest). */
class AgSalesAllocationTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final UUID FARM = UUID.randomUUID(), INVOICE = UUID.randomUUID(), LINE = UUID.randomUUID(), TARGET = UUID.randomUUID(), USER = UUID.randomUUID();
    private static final LocalDate SOLD = LocalDate.of(2026, 9, 20);

    private static AgSalesAllocation allocation(String targetType, String qty, Integer heads) {
        return AgSalesAllocation.create(TENANT, FARM, INVOICE, LINE, "INV-0042", "Broiler chicken", targetType, TARGET, new BigDecimal(qty), "  kg ", heads, SOLD, "to ABC Foods", USER);
    }

    @Test
    @DisplayName("a new allocation is ACTIVE and keeps what it was given, tidied")
    void createsActive() {
        AgSalesAllocation a = allocation("GROUP", "2100", 1000);
        assertEquals("ACTIVE", a.getStatus());
        assertEquals("kg", a.getUnit());
        assertEquals(0, new BigDecimal("2100").compareTo(a.getQuantity()));
        assertEquals(1000, a.getHeadCount().intValue());
        assertEquals(INVOICE, a.getInvoiceId());
        assertEquals(LINE, a.getInvoiceLineId());
        assertEquals("INV-0042", a.getInvoiceNumber());
        assertEquals(SOLD, a.getSoldOn());
        assertEquals(USER, a.getCreatedBy());
        assertNull(a.getRemovedAt());
        assertNotNull(a.getCreatedAt());
    }

    @Test
    @DisplayName("it stores no money: revenue is computed live from the invoice")
    void storesNoMoney() {
        for (java.lang.reflect.Field f : AgSalesAllocation.class.getDeclaredFields()) {
            String n = f.getName().toLowerCase();
            assertFalse(n.contains("revenue") || n.contains("amount") || n.contains("price") || n.contains("total") || n.contains("value"), "unexpected money field: " + f.getName());
        }
    }

    @Test
    @DisplayName("every required field is checked")
    void validation() {
        BigDecimal q = BigDecimal.TEN;
        assertThrows(IllegalArgumentException.class, () -> AgSalesAllocation.create(null, FARM, INVOICE, LINE, "n", "d", "GROUP", TARGET, q, "kg", null, SOLD, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgSalesAllocation.create(TENANT, null, INVOICE, LINE, "n", "d", "GROUP", TARGET, q, "kg", null, SOLD, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgSalesAllocation.create(TENANT, FARM, null, LINE, "n", "d", "GROUP", TARGET, q, "kg", null, SOLD, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgSalesAllocation.create(TENANT, FARM, INVOICE, null, "n", "d", "GROUP", TARGET, q, "kg", null, SOLD, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgSalesAllocation.create(TENANT, FARM, INVOICE, LINE, "n", "d", "TRACTOR", TARGET, q, "kg", null, SOLD, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgSalesAllocation.create(TENANT, FARM, INVOICE, LINE, "n", "d", "GROUP", null, q, "kg", null, SOLD, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgSalesAllocation.create(TENANT, FARM, INVOICE, LINE, "n", "d", "GROUP", TARGET, BigDecimal.ZERO, "kg", null, SOLD, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgSalesAllocation.create(TENANT, FARM, INVOICE, LINE, "n", "d", "GROUP", TARGET, new BigDecimal("-1"), "kg", null, SOLD, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgSalesAllocation.create(TENANT, FARM, INVOICE, LINE, "n", "d", "GROUP", TARGET, null, "kg", null, SOLD, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgSalesAllocation.create(TENANT, FARM, INVOICE, LINE, "n", "d", "GROUP", TARGET, q, "kg", 0, SOLD, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgSalesAllocation.create(TENANT, FARM, INVOICE, LINE, "n", "d", "GROUP", TARGET, q, "kg", -3, SOLD, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgSalesAllocation.create(TENANT, FARM, INVOICE, LINE, "n", "d", "GROUP", TARGET, q, "kg", null, null, null, USER));
    }

    @Test
    @DisplayName("all four target types are accepted; a single animal is exactly one head")
    void targetsAndHeads() {
        for (String t : new String[] {"CROP_CYCLE", "GROUP", "ANIMAL", "ENTERPRISE"}) assertDoesNotThrow(() -> allocation(t, "5", null));
        assertDoesNotThrow(() -> allocation("ANIMAL", "480", 1));
        assertThrows(IllegalArgumentException.class, () -> allocation("ANIMAL", "480", 2));
        assertDoesNotThrow(() -> allocation("GROUP", "480", 200));
    }

    @Test
    @DisplayName("a long description is cut to fit, and a blank unit is none")
    void tidying() {
        AgSalesAllocation a = AgSalesAllocation.create(TENANT, FARM, INVOICE, LINE, "n", "d".repeat(300), "GROUP", TARGET, BigDecimal.ONE, "  ", null, SOLD, null, USER);
        assertEquals(255, a.getDescription().length());
        assertNull(a.getUnit());
    }

    @Test
    @DisplayName("remove() takes it out of revenue, recording who and when, and can only happen once")
    void removeOnce() {
        AgSalesAllocation a = allocation("GROUP", "10", null);
        UUID by = UUID.randomUUID();

        a.remove(by);

        assertEquals("REMOVED", a.getStatus());
        assertEquals(by, a.getRemovedBy());
        assertNotNull(a.getRemovedAt());
        assertThrows(IllegalStateException.class, () -> a.remove(by));
        assertEquals("REMOVED", a.getStatus());
    }
}

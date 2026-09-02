package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgInventoryItemTest {

    private AgInventoryItem newItem(BigDecimal reorderLevel) {
        return AgInventoryItem.create(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), "Broiler Starter",
                "FEED", "kg", reorderLevel, new BigDecimal("12.50"), "AgriFeeds Ltd");
    }

    @Test
    @DisplayName("issue() more than currentQuantity throws IllegalStateException and leaves quantity unchanged")
    void issueInsufficientStockThrows() {
        AgInventoryItem item = newItem(new BigDecimal("50"));
        item.receive(new BigDecimal("100"), null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> item.issue(new BigDecimal("150")));
        assertTrue(ex.getMessage().contains("Broiler Starter"));
        assertEquals(0, new BigDecimal("100").compareTo(item.getCurrentQuantity()),
                "currentQuantity must be unchanged after a rejected issue");
    }

    @Test
    @DisplayName("issue() exactly the available quantity succeeds and leaves zero on hand")
    void issueExactAvailableSucceeds() {
        AgInventoryItem item = newItem(new BigDecimal("10"));
        item.receive(new BigDecimal("30"), null);
        item.issue(new BigDecimal("30"));
        assertEquals(0, BigDecimal.ZERO.compareTo(item.getCurrentQuantity()));
    }

    @Test
    @DisplayName("isBelowReorderLevel() is true only once currentQuantity drops under reorderLevel")
    void belowReorderLevelReflectsQuantity() {
        AgInventoryItem item = newItem(new BigDecimal("20"));
        item.receive(new BigDecimal("25"), null);
        assertFalse(item.isBelowReorderLevel(), "25 on hand, reorder at 20 — not yet below");

        item.issue(new BigDecimal("10"));
        assertTrue(item.isBelowReorderLevel(), "15 on hand, reorder at 20 — now below");
    }

    @Test
    @DisplayName("isBelowReorderLevel() is false when no reorderLevel is set")
    void belowReorderLevelFalseWithNoReorderLevel() {
        AgInventoryItem item = newItem(null);
        assertFalse(item.isBelowReorderLevel());
    }

    @Test
    @DisplayName("adjust() sets currentQuantity directly and rejects negative values")
    void adjustSetsQuantityDirectly() {
        AgInventoryItem item = newItem(new BigDecimal("5"));
        item.adjust(new BigDecimal("42"));
        assertEquals(0, new BigDecimal("42").compareTo(item.getCurrentQuantity()));
        assertThrows(IllegalArgumentException.class, () -> item.adjust(new BigDecimal("-1")));
    }
}

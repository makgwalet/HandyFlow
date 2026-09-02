package za.co.handyflow.platform.warehousing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhseOutboundOrderLineTest {

    private WhseOutboundOrderLine newLine(BigDecimal qtyOrdered) {
        return WhseOutboundOrderLine.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), qtyOrdered, null);
    }

    @Test
    void createRejectsNonPositiveQtyOrdered() {
        assertThrows(IllegalArgumentException.class,
                () -> WhseOutboundOrderLine.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, null));
    }

    @Test
    void markPickedAccumulates() {
        WhseOutboundOrderLine line = newLine(new BigDecimal("10"));
        line.markPicked(new BigDecimal("4"));
        line.markPicked(new BigDecimal("6"));
        assertEquals(new BigDecimal("10"), line.getQtyPicked());
        assertTrue(line.isFullyPicked());
    }

    @Test
    void markPickedRejectsExceedingQtyOrdered() {
        WhseOutboundOrderLine line = newLine(new BigDecimal("10"));
        line.markPicked(new BigDecimal("8"));
        assertThrows(IllegalStateException.class, () -> line.markPicked(new BigDecimal("3")));
    }

    @Test
    void markPickedRejectsNonPositiveQty() {
        WhseOutboundOrderLine line = newLine(new BigDecimal("10"));
        assertThrows(IllegalArgumentException.class, () -> line.markPicked(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> line.markPicked(new BigDecimal("-1")));
    }

    @Test
    void allocateSetsLocationId() {
        WhseOutboundOrderLine line = newLine(new BigDecimal("10"));
        assertNull(line.getLocationId());
        UUID locationId = UUID.randomUUID();
        line.allocate(locationId);
        assertEquals(locationId, line.getLocationId());
    }
}

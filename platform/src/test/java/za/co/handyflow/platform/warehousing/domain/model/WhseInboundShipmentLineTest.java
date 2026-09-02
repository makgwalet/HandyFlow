package za.co.handyflow.platform.warehousing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhseInboundShipmentLineTest {

    private WhseInboundShipmentLine newLine(BigDecimal expectedQty) {
        return WhseInboundShipmentLine.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), expectedQty, null);
    }

    @Test
    void createRejectsNonPositiveExpectedQty() {
        assertThrows(IllegalArgumentException.class,
                () -> WhseInboundShipmentLine.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.ZERO, null));
    }

    @Test
    void receiveRequiresLocation() {
        WhseInboundShipmentLine line = newLine(new BigDecimal("10"));
        assertThrows(IllegalArgumentException.class, () -> line.receive(new BigDecimal("5"), null));
    }

    @Test
    void receiveRequiresPositiveQty() {
        WhseInboundShipmentLine line = newLine(new BigDecimal("10"));
        UUID locationId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> line.receive(BigDecimal.ZERO, locationId));
        assertThrows(IllegalArgumentException.class, () -> line.receive(new BigDecimal("-1"), locationId));
    }

    @Test
    void receiveIsCumulativeAcrossMultiplePasses() {
        WhseInboundShipmentLine line = newLine(new BigDecimal("10"));
        UUID locationId = UUID.randomUUID();
        line.receive(new BigDecimal("4"), locationId);
        line.receive(new BigDecimal("6"), locationId);
        assertEquals(new BigDecimal("10"), line.getReceivedQty());
        assertTrue(line.isFullyReceived());
        assertEquals(BigDecimal.ZERO, line.outstandingQty());
    }

    @Test
    void outstandingQtyNeverGoesNegative() {
        WhseInboundShipmentLine line = newLine(new BigDecimal("5"));
        line.receive(new BigDecimal("8"), UUID.randomUUID()); // over-receipt — allowed by this entity, flagged as a real-world possibility (short then top-up, or an over-delivery)
        assertEquals(BigDecimal.ZERO, line.outstandingQty());
        assertTrue(line.isFullyReceived());
    }

    @Test
    void isFullyReceivedFalseWhenPartial() {
        WhseInboundShipmentLine line = newLine(new BigDecimal("10"));
        line.receive(new BigDecimal("4"), UUID.randomUUID());
        assertFalse(line.isFullyReceived());
        assertEquals(new BigDecimal("6"), line.outstandingQty());
    }
}

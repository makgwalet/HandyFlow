package za.co.handyflow.platform.warehousing.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhseInboundShipmentTest {

    private WhseInboundShipment newShipment() {
        return WhseInboundShipment.create(UUID.randomUUID(), UUID.randomUUID(), "ASN-1", LocalDate.now().plusDays(2), null);
    }

    @Test
    void newShipmentStartsExpected() {
        WhseInboundShipment shipment = newShipment();
        assertEquals("EXPECTED", shipment.getStatus());
        assertFalse(shipment.isTerminal());
    }

    @Test
    void markReceivedSetsReceivedDateAndIsTerminal() {
        WhseInboundShipment shipment = newShipment();
        shipment.markReceived();
        assertEquals("RECEIVED", shipment.getStatus());
        assertNotNull(shipment.getReceivedDate());
        assertTrue(shipment.isTerminal());
    }

    @Test
    void cannotChangeAShipmentThatIsAlreadyTerminal() {
        WhseInboundShipment received = newShipment();
        received.markReceived();
        assertThrows(IllegalStateException.class, received::markPartiallyReceived);
        assertThrows(IllegalStateException.class, received::cancel);

        WhseInboundShipment cancelled = newShipment();
        cancelled.cancel();
        assertThrows(IllegalStateException.class, cancelled::markReceived);
    }

    @Test
    void markPartiallyReceivedIsNotTerminal() {
        WhseInboundShipment shipment = newShipment();
        shipment.markPartiallyReceived();
        assertEquals("PARTIALLY_RECEIVED", shipment.getStatus());
        assertFalse(shipment.isTerminal());
    }
}

package za.co.handyflow.platform.warehousing.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WhseOutboundOrderTest {

    private WhseOutboundOrder newOrder() {
        return WhseOutboundOrder.create(UUID.randomUUID(), UUID.randomUUID(), "ORD-1", "Acme Retail",
                "1 Main St", LocalDate.now().plusDays(3), null);
    }

    @Test
    void newOrderStartsPending() {
        assertEquals("PENDING", newOrder().getStatus());
    }

    @Test
    void fullLifecycleTransitionsInOrder() {
        WhseOutboundOrder order = newOrder();
        order.startPicking();
        assertEquals("PICKING", order.getStatus());
        order.markPacked();
        assertEquals("PACKED", order.getStatus());
        order.markShipped("Courier Co", "TRK123");
        assertEquals("SHIPPED", order.getStatus());
        assertEquals("Courier Co", order.getCarrier());
        assertEquals("TRK123", order.getTrackingNumber());
        assertNotNull(order.getShippedDate());
    }

    @Test
    void startPickingRejectsFromNonPendingStatus() {
        WhseOutboundOrder order = newOrder();
        order.startPicking();
        assertThrows(IllegalStateException.class, order::startPicking);
    }

    @Test
    void markPackedRequiresPicking() {
        WhseOutboundOrder order = newOrder();
        assertThrows(IllegalStateException.class, order::markPacked);
    }

    @Test
    void markShippedRequiresPacked() {
        WhseOutboundOrder order = newOrder();
        order.startPicking();
        assertThrows(IllegalStateException.class, () -> order.markShipped("X", "Y"));
    }

    @Test
    void cancelAllowedFromPendingAndPicking() {
        WhseOutboundOrder pendingOrder = newOrder();
        pendingOrder.cancel();
        assertEquals("CANCELLED", pendingOrder.getStatus());

        WhseOutboundOrder pickingOrder = newOrder();
        pickingOrder.startPicking();
        pickingOrder.cancel();
        assertEquals("CANCELLED", pickingOrder.getStatus());
    }

    @Test
    void cancelRejectedOncePacked() {
        WhseOutboundOrder order = newOrder();
        order.startPicking();
        order.markPacked();
        assertThrows(IllegalStateException.class, order::cancel);
    }
}

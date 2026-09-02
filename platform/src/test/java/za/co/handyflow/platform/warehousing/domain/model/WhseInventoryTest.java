package za.co.handyflow.platform.warehousing.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The centerpiece reservation-workflow entity — no equivalent complexity
 * existed in Collections Agency (which has no allocation concept at all).
 * These guards are what stop two outbound orders ever drawing down the
 * same physical stock, and what stop on-hand or allocated quantity from
 * ever going negative.
 */
class WhseInventoryTest {

    private WhseInventory newPosition() {
        return WhseInventory.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void newPositionStartsAtZero() {
        WhseInventory inv = newPosition();
        assertEquals(BigDecimal.ZERO, inv.getQtyOnHand());
        assertEquals(BigDecimal.ZERO, inv.getQtyAllocated());
        assertEquals(BigDecimal.ZERO, inv.available());
    }

    @Test
    void increaseOnHandAddsQty() {
        WhseInventory inv = newPosition();
        inv.increaseOnHand(new BigDecimal("10"));
        assertEquals(new BigDecimal("10"), inv.getQtyOnHand());
        assertEquals(new BigDecimal("10"), inv.available());
    }

    @Test
    void increaseOnHandRejectsNonPositiveQty() {
        WhseInventory inv = newPosition();
        assertThrows(IllegalArgumentException.class, () -> inv.increaseOnHand(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> inv.increaseOnHand(new BigDecimal("-1")));
    }

    @Test
    void adjustOnHandAllowsNegativeDeltaAboveZero() {
        WhseInventory inv = newPosition();
        inv.increaseOnHand(new BigDecimal("10"));
        inv.adjustOnHand(new BigDecimal("-4"));
        assertEquals(new BigDecimal("6"), inv.getQtyOnHand());
    }

    @Test
    void adjustOnHandRejectsResultBelowZero() {
        WhseInventory inv = newPosition();
        inv.increaseOnHand(new BigDecimal("5"));
        assertThrows(IllegalStateException.class, () -> inv.adjustOnHand(new BigDecimal("-6")));
    }

    @Test
    void adjustOnHandRejectsZeroDelta() {
        WhseInventory inv = newPosition();
        assertThrows(IllegalArgumentException.class, () -> inv.adjustOnHand(BigDecimal.ZERO));
    }

    @Test
    void allocateReducesAvailableNotOnHand() {
        WhseInventory inv = newPosition();
        inv.increaseOnHand(new BigDecimal("10"));
        inv.allocate(new BigDecimal("4"));
        assertEquals(new BigDecimal("10"), inv.getQtyOnHand());
        assertEquals(new BigDecimal("4"), inv.getQtyAllocated());
        assertEquals(new BigDecimal("6"), inv.available());
    }

    @Test
    void allocateRejectsMoreThanAvailable() {
        WhseInventory inv = newPosition();
        inv.increaseOnHand(new BigDecimal("10"));
        inv.allocate(new BigDecimal("7"));
        assertThrows(IllegalStateException.class, () -> inv.allocate(new BigDecimal("4")));
    }

    @Test
    void deallocateReleasesAllocatedQty() {
        WhseInventory inv = newPosition();
        inv.increaseOnHand(new BigDecimal("10"));
        inv.allocate(new BigDecimal("6"));
        inv.deallocate(new BigDecimal("6"));
        assertEquals(BigDecimal.ZERO, inv.getQtyAllocated());
        assertEquals(new BigDecimal("10"), inv.available());
    }

    @Test
    void deallocateRejectsMoreThanAllocated() {
        WhseInventory inv = newPosition();
        inv.increaseOnHand(new BigDecimal("10"));
        inv.allocate(new BigDecimal("3"));
        assertThrows(IllegalStateException.class, () -> inv.deallocate(new BigDecimal("4")));
    }

    @Test
    void fulfillAllocationReducesBothOnHandAndAllocated() {
        WhseInventory inv = newPosition();
        inv.increaseOnHand(new BigDecimal("10"));
        inv.allocate(new BigDecimal("6"));
        inv.fulfillAllocation(new BigDecimal("6"));
        assertEquals(new BigDecimal("4"), inv.getQtyOnHand());
        assertEquals(BigDecimal.ZERO, inv.getQtyAllocated());
        assertEquals(new BigDecimal("4"), inv.available());
    }

    @Test
    void fulfillAllocationRejectsMoreThanAllocated() {
        WhseInventory inv = newPosition();
        inv.increaseOnHand(new BigDecimal("10"));
        inv.allocate(new BigDecimal("3"));
        assertThrows(IllegalStateException.class, () -> inv.fulfillAllocation(new BigDecimal("4")));
    }

    @Test
    void fulfillAllocationRejectsMoreThanOnHand() {
        // Pathological state that should never occur given the other guards, but the entity defends against it
        // independently rather than trusting the caller — same "belt and braces" convention as every other guard here.
        WhseInventory inv = newPosition();
        inv.increaseOnHand(new BigDecimal("5"));
        inv.allocate(new BigDecimal("5"));
        inv.adjustOnHand(new BigDecimal("-2")); // now qtyOnHand=3, qtyAllocated=5 — an inconsistent state deliberately forced for this test
        assertThrows(IllegalStateException.class, () -> inv.fulfillAllocation(new BigDecimal("5")));
    }

    @Test
    void allocateRejectsNonPositiveQty() {
        WhseInventory inv = newPosition();
        inv.increaseOnHand(new BigDecimal("10"));
        assertThrows(IllegalArgumentException.class, () -> inv.allocate(BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> inv.allocate(new BigDecimal("-1")));
    }
}

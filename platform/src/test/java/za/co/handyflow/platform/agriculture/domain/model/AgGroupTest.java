package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure entity-behavior tests — no Spring context, matching FmAssetTest/
 * FmWorkOrderTest's own plain-JUnit convention for domain model classes.
 */
class AgGroupTest {

    private AgGroup newGroup(int initialCount) {
        return AgGroup.create(TenantId.of(UUID.randomUUID()), UUID.randomUUID(), null, null,
                UUID.randomUUID(), "BATCH-001", "Ross 308", initialCount, LocalDate.now(), "PURCHASED");
    }

    @Test
    @DisplayName("reduceCount by more than currentCount throws IllegalStateException")
    void reduceCountOverReductionThrows() {
        AgGroup group = newGroup(50);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> group.reduceCount(51));
        assertTrue(ex.getMessage().contains("BATCH-001"));
        assertEquals(50, group.getCurrentCount(), "count must be unchanged after a rejected reduction");
        assertEquals("ACTIVE", group.getStatus());
    }

    @Test
    @DisplayName("reduceCount to exactly zero auto-closes the group")
    void reduceCountToZeroAutoCloses() {
        AgGroup group = newGroup(20);
        group.reduceCount(20);
        assertEquals(0, group.getCurrentCount());
        assertEquals("CLOSED", group.getStatus());
    }

    @Test
    @DisplayName("reduceCount partially leaves the group ACTIVE")
    void reduceCountPartiallyLeavesActive() {
        AgGroup group = newGroup(20);
        group.reduceCount(5);
        assertEquals(15, group.getCurrentCount());
        assertEquals("ACTIVE", group.getStatus());
    }

    @Test
    @DisplayName("reduceCount with a non-positive amount throws IllegalArgumentException")
    void reduceCountNonPositiveThrows() {
        AgGroup group = newGroup(20);
        assertThrows(IllegalArgumentException.class, () -> group.reduceCount(0));
        assertThrows(IllegalArgumentException.class, () -> group.reduceCount(-3));
    }

    @Test
    @DisplayName("increaseCount grows currentCount without touching status")
    void increaseCountGrowsCount() {
        AgGroup group = newGroup(10);
        group.increaseCount(5);
        assertEquals(15, group.getCurrentCount());
        assertEquals("ACTIVE", group.getStatus());
    }

    @Test
    @DisplayName("close() then reopen() round-trips status")
    void closeThenReopen() {
        AgGroup group = newGroup(10);
        group.close();
        assertEquals("CLOSED", group.getStatus());
        group.reopen();
        assertEquals("ACTIVE", group.getStatus());
    }
}

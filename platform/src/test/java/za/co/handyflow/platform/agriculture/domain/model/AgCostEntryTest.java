package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pure entity-behaviour tests, no Spring context (same convention as AgCropCycleTest). */
class AgCostEntryTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final UUID FARM = UUID.randomUUID(), TARGET = UUID.randomUUID(), GROUP = UUID.randomUUID(), USER = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 9, 15);

    private static AgCostEntry entry(String amount) {
        return AgCostEntry.create(TENANT, FARM, DATE, "OTHER_DIRECT", "  Hired sprayer  ", "MANUAL", null, "CROP_CYCLE", TARGET,
                new BigDecimal("3"), " hours ", null, new BigDecimal(amount), new BigDecimal("100"), GROUP, "invoice 4471", USER);
    }

    @Test
    @DisplayName("a new entry is ACTIVE and keeps what it was given, tidied")
    void createsActive() {
        AgCostEntry e = entry("1250.50");
        assertEquals("ACTIVE", e.getStatus());
        assertEquals("Hired sprayer", e.getDescription());
        assertEquals("hours", e.getUnit());
        assertEquals(0, new BigDecimal("1250.50").compareTo(e.getAmount()));
        assertEquals(TARGET, e.getTargetId());
        assertEquals(GROUP, e.getAllocationGroupId());
        assertEquals(USER, e.getCreatedBy());
        assertNull(e.getReversesEntryId());
        assertNotNull(e.getCreatedAt());
    }

    @Test
    @DisplayName("every required field is checked")
    void validation() {
        BigDecimal pct = new BigDecimal("100"), amt = new BigDecimal("10");
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(null, FARM, DATE, "FUEL", "x", "MANUAL", null, "GROUP", TARGET, null, null, null, amt, pct, GROUP, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(TENANT, null, DATE, "FUEL", "x", "MANUAL", null, "GROUP", TARGET, null, null, null, amt, pct, GROUP, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(TENANT, FARM, null, "FUEL", "x", "MANUAL", null, "GROUP", TARGET, null, null, null, amt, pct, GROUP, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(TENANT, FARM, DATE, "RENT", "x", "MANUAL", null, "GROUP", TARGET, null, null, null, amt, pct, GROUP, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(TENANT, FARM, DATE, "FUEL", "  ", "MANUAL", null, "GROUP", TARGET, null, null, null, amt, pct, GROUP, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(TENANT, FARM, DATE, "FUEL", "x", " ", null, "GROUP", TARGET, null, null, null, amt, pct, GROUP, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(TENANT, FARM, DATE, "FUEL", "x", "MANUAL", null, "TRACTOR", TARGET, null, null, null, amt, pct, GROUP, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(TENANT, FARM, DATE, "FUEL", "x", "MANUAL", null, "GROUP", null, null, null, null, amt, pct, GROUP, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(TENANT, FARM, DATE, "FUEL", "x", "MANUAL", null, "GROUP", TARGET, null, null, null, BigDecimal.ZERO, pct, GROUP, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(TENANT, FARM, DATE, "FUEL", "x", "MANUAL", null, "GROUP", TARGET, null, null, null, amt, BigDecimal.ZERO, GROUP, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(TENANT, FARM, DATE, "FUEL", "x", "MANUAL", null, "GROUP", TARGET, null, null, null, amt, new BigDecimal("100.01"), GROUP, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(TENANT, FARM, DATE, "FUEL", "x", "MANUAL", null, "GROUP", TARGET, null, null, null, amt, pct, null, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(TENANT, FARM, DATE, "FUEL", "x", "MANUAL", null, "GROUP", TARGET, new BigDecimal("-1"), null, null, amt, pct, GROUP, null, USER));
        assertThrows(IllegalArgumentException.class, () -> AgCostEntry.create(TENANT, FARM, DATE, "FUEL", "x".repeat(256), "MANUAL", null, "GROUP", TARGET, null, null, null, amt, pct, GROUP, null, USER));
    }

    @Test
    @DisplayName("all four categories and all four target types are accepted")
    void acceptedValues() {
        for (String c : new String[] {"LABOUR", "EQUIPMENT", "FUEL", "OTHER_DIRECT"}) {
            assertDoesNotThrow(() -> AgCostEntry.create(TENANT, FARM, DATE, c, "x", "MANUAL", null, "GROUP", TARGET, null, null, null, BigDecimal.TEN, new BigDecimal("100"), GROUP, null, USER));
        }
        for (String t : new String[] {"CROP_CYCLE", "GROUP", "ANIMAL", "ENTERPRISE"}) {
            assertDoesNotThrow(() -> AgCostEntry.create(TENANT, FARM, DATE, "FUEL", "x", "MANUAL", null, t, TARGET, null, null, null, BigDecimal.TEN, new BigDecimal("100"), GROUP, null, USER));
        }
    }

    @Test
    @DisplayName("reverse() marks the original REVERSED and returns a negating REVERSAL row")
    void reverseCreatesNegatingRow() {
        AgCostEntry original = entry("1250.50");
        UUID reverser = UUID.randomUUID();

        AgCostEntry reversal = original.reverse("  Wrong crop  ", reverser);

        assertEquals("REVERSED", original.getStatus());
        assertEquals("REVERSAL", reversal.getStatus());
        assertEquals(0, new BigDecimal("-1250.50").compareTo(reversal.getAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(original.getAmount().add(reversal.getAmount())), "the pair nets to zero");
        assertEquals(original.getId(), reversal.getReversesEntryId());
        assertEquals("Wrong crop", reversal.getNotes());
        assertEquals(reverser, reversal.getCreatedBy());
        assertTrue(reversal.getDescription().startsWith("Reversal: Hired sprayer"));
        assertNotEquals(original.getId(), reversal.getId());
    }

    @Test
    @DisplayName("the reversal lands in the same period, on the same target and allocation group, with the same snapshot")
    void reversalMirrorsTheOriginal() {
        AgCostEntry original = AgCostEntry.create(TENANT, FARM, DATE, "LABOUR", "Weeding", "HR_LABOUR", UUID.randomUUID(), "GROUP", TARGET,
                new BigDecimal("4"), "h", new BigDecimal("65.0000"), new BigDecimal("260.00"), new BigDecimal("40"), GROUP, null, USER);

        AgCostEntry r = original.reverse(null, USER);

        assertEquals(DATE, r.getEntryDate());
        assertEquals("LABOUR", r.getCategory());
        assertEquals("GROUP", r.getTargetType());
        assertEquals(TARGET, r.getTargetId());
        assertEquals(GROUP, r.getAllocationGroupId());
        assertEquals(original.getSourceRef(), r.getSourceRef());
        assertEquals(0, new BigDecimal("65").compareTo(r.getRate()));
        assertEquals(0, new BigDecimal("4").compareTo(r.getQuantity()));
        assertEquals(0, new BigDecimal("40").compareTo(r.getPercentage()));
        assertNull(r.getNotes(), "no reason given");
    }

    @Test
    @DisplayName("an entry can be reversed only once, and a reversal cannot itself be reversed")
    void reverseOnce() {
        AgCostEntry original = entry("100");
        AgCostEntry reversal = original.reverse("x", USER);

        assertThrows(IllegalStateException.class, () -> original.reverse("again", USER));
        assertThrows(IllegalStateException.class, () -> reversal.reverse("undo", USER));
        assertEquals("REVERSED", original.getStatus());
    }

    @Test
    @DisplayName("a long description is cut to fit when prefixed with 'Reversal:'")
    void longDescriptionIsTruncated() {
        AgCostEntry original = AgCostEntry.create(TENANT, FARM, DATE, "OTHER_DIRECT", "d".repeat(255), "MANUAL", null, "GROUP", TARGET,
                null, null, null, BigDecimal.TEN, new BigDecimal("100"), GROUP, null, USER);
        assertEquals(255, original.reverse(null, USER).getDescription().length());
    }
}

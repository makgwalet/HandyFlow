package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pure entity-behaviour tests, no Spring context (same convention as AgCropCycleTest). */
class AgSeasonTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    private AgSeason newSeason() {
        return AgSeason.create(TENANT, UUID.randomUUID(), "2026/27 summer", START, null, null);
    }

    @Test
    @DisplayName("a new season starts in PLANNING")
    void startsPlanning() {
        assertEquals("PLANNING", newSeason().getStatus());
    }

    @Test
    @DisplayName("activate() moves PLANNING to ACTIVE and refuses a second activation")
    void activateOnce() {
        AgSeason season = newSeason();
        season.activate();
        assertEquals("ACTIVE", season.getStatus());
        assertThrows(IllegalStateException.class, season::activate);
    }

    @Test
    @DisplayName("close() needs an active season; a closed season can be reopened")
    void closeAndReopen() {
        AgSeason season = newSeason();
        assertThrows(IllegalStateException.class, season::close);      // never started
        assertEquals("PLANNING", season.getStatus());

        season.activate();
        season.close();
        assertEquals("CLOSED", season.getStatus());
        assertThrows(IllegalStateException.class, season::close);      // already closed

        season.activate();                                             // reopen
        assertEquals("ACTIVE", season.getStatus());
    }

    @Test
    @DisplayName("update() checks the end date against the start date, using the existing start when none is given")
    void updateChecksDates() {
        AgSeason season = newSeason();
        assertThrows(IllegalArgumentException.class, () -> season.update(null, null, START.minusDays(1), null));
        assertThrows(IllegalArgumentException.class, () -> season.update(null, START.plusMonths(6), START.plusMonths(1), null));

        season.update("Renamed", null, START.plusMonths(6), "note");
        assertEquals("Renamed", season.getName());
        assertEquals(START.plusMonths(6), season.getEndDate());
        assertEquals(START, season.getStartDate());
    }

    @Test
    @DisplayName("update() replaces the end date and notes, so null clears them")
    void updateReplacesEndDateAndNotes() {
        AgSeason season = AgSeason.create(TENANT, UUID.randomUUID(), "S", START, START.plusMonths(6), "keep?");
        season.update(null, null, null, null);
        assertNull(season.getEndDate());
        assertNull(season.getNotes());
    }

    @Test
    @DisplayName("create() still rejects an end date before the start date")
    void createChecksDates() {
        assertThrows(IllegalArgumentException.class, () -> AgSeason.create(TENANT, UUID.randomUUID(), "S", START, START.minusDays(1), null));
    }
}

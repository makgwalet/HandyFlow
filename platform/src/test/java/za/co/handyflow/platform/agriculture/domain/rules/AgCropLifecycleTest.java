package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.agriculture.domain.rules.AgCropLifecycle.RecordKind;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgCropLifecycleTest {

    private static final List<String> IN_THE_GROUND = List.of("PLANTED", "GROWING", "HARVESTING");
    private static final List<String> TERMINAL = List.of("HARVESTED", "FAILED", "ABANDONED");

    @Test
    @DisplayName("a crop can fail only once it is in the ground")
    void failOnlyWhenInTheGround() {
        for (String s : IN_THE_GROUND) assertDoesNotThrow(() -> AgCropLifecycle.requireCanFail(s));
        IllegalStateException planned = assertThrows(IllegalStateException.class, () -> AgCropLifecycle.requireCanFail("PLANNED"));
        assertTrue(planned.getMessage().contains("PLANNED"));
        for (String s : TERMINAL) assertThrows(IllegalStateException.class, () -> AgCropLifecycle.requireCanFail(s), s);
    }

    @Test
    @DisplayName("any live cycle can be abandoned, a finished one cannot")
    void abandonOnlyWhenLive() {
        for (String s : List.of("PLANNED", "PLANTED", "GROWING", "HARVESTING")) assertDoesNotThrow(() -> AgCropLifecycle.requireCanAbandon(s));
        for (String s : TERMINAL) assertThrows(IllegalStateException.class, () -> AgCropLifecycle.requireCanAbandon(s), s);
    }

    @Test
    @DisplayName("inputs and scouting are refused on failed or abandoned cycles only")
    void inputsAndScoutingRules() {
        for (RecordKind kind : List.of(RecordKind.INPUT, RecordKind.SCOUTING)) {
            for (String s : List.of("PLANNED", "PLANTED", "GROWING", "HARVESTING", "HARVESTED")) {
                assertDoesNotThrow(() -> AgCropLifecycle.requireAcceptsRecords(kind, s), kind + " on " + s);
            }
            for (String s : List.of("FAILED", "ABANDONED")) {
                assertThrows(IllegalStateException.class, () -> AgCropLifecycle.requireAcceptsRecords(kind, s), kind + " on " + s);
            }
        }
    }

    @Test
    @DisplayName("a harvest also needs something planted")
    void harvestRules() {
        for (String s : List.of("PLANTED", "GROWING", "HARVESTING", "HARVESTED")) assertDoesNotThrow(() -> AgCropLifecycle.requireAcceptsRecords(RecordKind.HARVEST, s));
        IllegalStateException planned = assertThrows(IllegalStateException.class, () -> AgCropLifecycle.requireAcceptsRecords(RecordKind.HARVEST, "PLANNED"));
        assertTrue(planned.getMessage().contains("not been planted"));
        assertThrows(IllegalStateException.class, () -> AgCropLifecycle.requireAcceptsRecords(RecordKind.HARVEST, "FAILED"));
    }

    @Test
    @DisplayName("refusal messages name what was refused and why")
    void messagesAreReadable() {
        assertEquals("cannot record an input on a failed crop cycle",
                assertThrows(IllegalStateException.class, () -> AgCropLifecycle.requireAcceptsRecords(RecordKind.INPUT, "FAILED")).getMessage());
        assertEquals("cannot record scouting on an abandoned crop cycle",
                assertThrows(IllegalStateException.class, () -> AgCropLifecycle.requireAcceptsRecords(RecordKind.SCOUTING, "ABANDONED")).getMessage());
    }

    @Test
    @DisplayName("expected harvest cannot be before planting; unknown dates are not checked")
    void harvestNotBeforePlanting() {
        LocalDate planting = LocalDate.of(2026, 10, 15);
        assertDoesNotThrow(() -> AgCropLifecycle.requireHarvestNotBeforePlanting(planting, planting));
        assertDoesNotThrow(() -> AgCropLifecycle.requireHarvestNotBeforePlanting(planting, planting.plusDays(150)));
        assertDoesNotThrow(() -> AgCropLifecycle.requireHarvestNotBeforePlanting(null, planting));
        assertDoesNotThrow(() -> AgCropLifecycle.requireHarvestNotBeforePlanting(planting, null));
        assertThrows(IllegalArgumentException.class, () -> AgCropLifecycle.requireHarvestNotBeforePlanting(planting, planting.minusDays(1)));
    }

    @Test
    @DisplayName("terminal statuses")
    void terminal() {
        for (String s : TERMINAL) assertTrue(AgCropLifecycle.isTerminal(s), s);
        for (String s : List.of("PLANNED", "PLANTED", "GROWING", "HARVESTING")) assertFalse(AgCropLifecycle.isTerminal(s), s);
    }
}

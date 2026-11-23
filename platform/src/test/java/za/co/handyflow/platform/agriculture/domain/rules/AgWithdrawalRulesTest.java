package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.Hold;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.Treatment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AgWithdrawalRulesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);

    private static Treatment t(LocalDate date, Integer days, String product) { return new Treatment(date, days, product, false); }

    @Test void clearDayIsTheTreatmentDatePlusThePeriod() {
        assertEquals(LocalDate.of(2026, 12, 15), AgWithdrawalRules.clearOn(DAY, 14));
        assertNull(AgWithdrawalRules.clearOn(DAY, null));
        assertNull(AgWithdrawalRules.clearOn(DAY, 0));
        assertNull(AgWithdrawalRules.clearOn(null, 14));
    }

    @Test void heldUntilTheClearDayThenFree() {
        List<Treatment> list = List.of(t(DAY, 14, "Terramycin"));
        assertTrue(AgWithdrawalRules.activeHold(list, DAY).isPresent(), "same day");
        assertTrue(AgWithdrawalRules.activeHold(list, DAY.plusDays(13)).isPresent(), "last held day");
        assertTrue(AgWithdrawalRules.activeHold(list, DAY.plusDays(14)).isEmpty(), "clear on day 14");
        assertTrue(AgWithdrawalRules.activeHold(list, DAY.plusDays(30)).isEmpty());
    }

    @Test void theLongestRunningHoldIsTheOneReported() {
        List<Treatment> list = List.of(t(DAY, 7, "A"), t(DAY.minusDays(2), 30, "B"), t(DAY.minusDays(1), 5, "C"));
        Hold h = AgWithdrawalRules.activeHold(list, DAY).orElseThrow();
        assertEquals("B", h.product());
        assertEquals(DAY.plusDays(28), h.clearOn());
        assertEquals(DAY.minusDays(2), h.treatedOn());
    }

    @Test void aTreatmentAfterTheDateInQuestionDoesNotHoldIt() {
        // selling on 1 Dec is not held by a treatment given on 5 Dec
        assertTrue(AgWithdrawalRules.activeHold(List.of(t(DAY.plusDays(4), 14, "A")), DAY).isEmpty());
    }

    @Test void aScheduledTreatmentHasNotHappenedAndHoldsNothing() {
        assertTrue(AgWithdrawalRules.activeHold(List.of(new Treatment(DAY, 14, "A", true)), DAY).isEmpty());
    }

    @Test void noPeriodMeansNoHold() {
        assertTrue(AgWithdrawalRules.activeHold(List.of(t(DAY, null, "A"), t(DAY, 0, "B")), DAY).isEmpty());
        assertTrue(AgWithdrawalRules.activeHold(null, DAY).isEmpty());
        assertTrue(AgWithdrawalRules.activeHold(List.of(), DAY).isEmpty());
    }

    @Test void heldWithoutAReasonIsRefusedAndTheMessageSaysWhatAndUntilWhen() {
        Optional<Hold> hold = AgWithdrawalRules.activeHold(List.of(t(DAY, 14, "Terramycin LA")), DAY.plusDays(3));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AgWithdrawalRules.requireClear(hold, "animal tag AN-001 (Bessie)", "withdrawal period", "sold or slaughtered", null));
        assertTrue(e.getMessage().startsWith("Animal tag AN-001 (Bessie) is within the withdrawal period"), e.getMessage());
        assertTrue(e.getMessage().contains("Terramycin LA"), e.getMessage());
        assertTrue(e.getMessage().contains("1 Dec 2026"), e.getMessage());
        assertTrue(e.getMessage().contains("15 Dec 2026"), e.getMessage());
    }

    @Test void aTooShortReasonIsNotAReason() {
        Optional<Hold> hold = AgWithdrawalRules.activeHold(List.of(t(DAY, 14, null)), DAY);
        assertThrows(IllegalStateException.class, () -> AgWithdrawalRules.requireClear(hold, "x", "withdrawal period", "sold", "  ok"), "two characters");
        assertThrows(IllegalStateException.class, () -> AgWithdrawalRules.requireClear(hold, "x", "withdrawal period", "sold", ""));
        assertTrue(AgWithdrawalRules.requireClear(hold, "x", "withdrawal period", "sold", "Yes") != null, "three characters is enough");
    }

    @Test void aReasonLetsItThroughAndReturnsTheLineToKeep() {
        Optional<Hold> hold = AgWithdrawalRules.activeHold(List.of(t(DAY, 14, "A")), DAY);
        String line = AgWithdrawalRules.requireClear(hold, "x", "withdrawal period", "sold", "  Sold for breeding, not slaughter ");
        assertEquals("Override of withdrawal period (held until 15 Dec 2026): Sold for breeding, not slaughter", line);
    }

    @Test void nothingHeldPassesWithNoLine() {
        assertNull(AgWithdrawalRules.requireClear(Optional.empty(), "x", "withdrawal period", "sold", null));
        assertNull(AgWithdrawalRules.requireClear(Optional.empty(), "x", "withdrawal period", "sold", "a reason that is not needed"));
    }

    @Test void theOverrideLineIsAddedToExistingNotes() {
        assertEquals("line", AgWithdrawalRules.withOverrideNote(null, "line"));
        assertEquals("line", AgWithdrawalRules.withOverrideNote("  ", "line"));
        assertEquals("buyer: Jan\nline", AgWithdrawalRules.withOverrideNote("buyer: Jan ", "line"));
        assertEquals("notes", AgWithdrawalRules.withOverrideNote("notes", null));
        assertNull(AgWithdrawalRules.withOverrideNote(null, null));
    }

    @Test void periodsAreBetweenZeroAndTwoYears() {
        AgWithdrawalRules.requireValidDays(null, "p");
        AgWithdrawalRules.requireValidDays(0, "p");
        AgWithdrawalRules.requireValidDays(730, "p");
        assertThrows(IllegalArgumentException.class, () -> AgWithdrawalRules.requireValidDays(-1, "p"));
        assertThrows(IllegalArgumentException.class, () -> AgWithdrawalRules.requireValidDays(731, "p"));
    }

    @Test void onlySalesCullsAndTransfersOutAreHeld() {
        assertTrue(AgWithdrawalRules.LEAVES_FOR_MARKET.contains("SOLD"));
        assertTrue(AgWithdrawalRules.LEAVES_FOR_MARKET.contains("CULLED"));
        assertTrue(AgWithdrawalRules.LEAVES_FOR_MARKET.contains("TRANSFERRED_OUT"));
        assertFalse(AgWithdrawalRules.LEAVES_FOR_MARKET.contains("DECEASED"));
        assertFalse(AgWithdrawalRules.LEAVES_FOR_MARKET.contains("ACTIVE"));
    }
}

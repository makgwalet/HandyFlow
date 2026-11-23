package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.Produce;
import za.co.handyflow.platform.agriculture.domain.rules.AgWithdrawalRules.Treatment;
import za.co.handyflow.platform.agriculture.dto.HealthEventResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Milk and egg withdrawal use the same clear-on rule as meat, each with its own number of days. */
class AgProduceWithdrawalTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);

    @Test void milkDaysAreTheMilkPeriodAndEggDaysTheEggPeriod() {
        assertEquals(Integer.valueOf(4), AgWithdrawalRules.produceDays(Produce.MILK, 4, 9));
        assertEquals(Integer.valueOf(9), AgWithdrawalRules.produceDays(Produce.EGGS, 4, 9));
        assertNull(AgWithdrawalRules.produceDays(Produce.EGGS, 4, null));
    }

    @Test void milkIsHeldUntilTheDayAfterTheLastWithdrawalDay() {
        List<Treatment> milk = List.of(new Treatment(DAY, AgWithdrawalRules.produceDays(Produce.MILK, 4, null), "Mastitis tube", false));
        assertTrue(AgWithdrawalRules.activeHold(milk, DAY.plusDays(3)).isPresent());
        assertEquals(DAY.plusDays(4), AgWithdrawalRules.activeHold(milk, DAY.plusDays(3)).get().clearOn());
        assertTrue(AgWithdrawalRules.activeHold(milk, DAY.plusDays(4)).isEmpty());
    }

    @Test void aMilkPeriodDoesNotHoldEggs() {
        List<Treatment> eggs = List.of(new Treatment(DAY, AgWithdrawalRules.produceDays(Produce.EGGS, 4, null), "Mastitis tube", false));
        assertTrue(AgWithdrawalRules.activeHold(eggs, DAY.plusDays(1)).isEmpty());
    }

    @Test void theLongestHoldWinsAndAScheduledTreatmentHoldsNothing() {
        List<Treatment> t = List.of(new Treatment(DAY, 3, "A", false), new Treatment(DAY, 10, "B", false), new Treatment(DAY, 30, "C", true));
        assertEquals("B", AgWithdrawalRules.activeHold(t, DAY.plusDays(1)).get().product());
    }

    @Test void aMilkOrEggPeriodOutOfRangeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> AgWithdrawalRules.requireValidDays(731, "Milk withdrawal period"));
        assertThrows(IllegalArgumentException.class, () -> AgWithdrawalRules.requireValidDays(-1, "Egg withdrawal period"));
        AgWithdrawalRules.requireValidDays(0, "Milk withdrawal period");
        AgWithdrawalRules.requireValidDays(null, "Egg withdrawal period");
    }

    @Test void theResponseShowsWhenMilkAndEggsClear() {
        HealthEventResponse r = new HealthEventResponse(UUID.randomUUID(), UUID.randomUUID(), null, "TREATMENT", DAY, "Mastitis",
                "Tube", null, null, null, null, BigDecimal.ONE, 14, null, false, "COMPLETED", null, Instant.now(), Instant.now(), 4, 7);
        assertEquals(DAY.plusDays(14), r.withdrawalClearOn());
        assertEquals(DAY.plusDays(4), r.milkClearOn());
        assertEquals(DAY.plusDays(7), r.eggClearOn());
        assertEquals(Integer.valueOf(4), r.milkWithdrawalDays());
    }

    @Test void theResponseWithoutMilkAndEggPeriodsStillBuilds() {
        HealthEventResponse r = new HealthEventResponse(UUID.randomUUID(), UUID.randomUUID(), null, "TREATMENT", DAY, "Mastitis",
                "Tube", null, null, null, null, BigDecimal.ONE, 14, null, false, "COMPLETED", null, Instant.now(), Instant.now());
        assertEquals(DAY.plusDays(14), r.withdrawalClearOn());
        assertNull(r.milkClearOn());
        assertNull(r.eggClearOn());
        assertNull(r.milkWithdrawalDays());
    }
}

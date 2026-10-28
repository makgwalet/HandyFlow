package za.co.handyflow.platform.training.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.training.domain.model.TrainingRequirementRules.Held;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrainingRequirementRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private static Held valid(LocalDate expiry) { return new Held("VALID", expiry); }

    @Test
    void aRequirementNamingNothingAppliesToEveryone() {
        assertTrue(TrainingRequirementRules.applies(null, null, "Clerk", "Admin"));
        assertTrue(TrainingRequirementRules.applies("  ", "", null, null));
    }

    @Test
    void titleAndDepartmentAreComparedIgnoringCaseAndSpaces() {
        assertTrue(TrainingRequirementRules.applies("forklift  operator", null, " Forklift Operator ", "Warehouse"));
        assertTrue(TrainingRequirementRules.applies(null, "WAREHOUSE", "Clerk", "warehouse"));
        assertFalse(TrainingRequirementRules.applies("Forklift Operator", null, "Clerk", "Warehouse"));
    }

    @Test
    void everyPartTheRequirementNamesMustMatch() {
        assertTrue(TrainingRequirementRules.applies("Driver", "Logistics", "Driver", "Logistics"));
        assertFalse(TrainingRequirementRules.applies("Driver", "Logistics", "Driver", "Sales"));
        assertFalse(TrainingRequirementRules.applies("Driver", "Logistics", "Driver", null));
        assertFalse(TrainingRequirementRules.applies("Driver", null, null, "Logistics"));
    }

    @Test
    void aCertificateWithNoExpiryCountsForever() {
        assertEquals(TrainingRequirementRules.OK, TrainingRequirementRules.status(List.of(valid(null)), false, TODAY));
    }

    @Test
    void aCertificateInDateIsValidAndOneWithinThirtyDaysIsExpiring() {
        assertEquals(TrainingRequirementRules.OK, TrainingRequirementRules.status(List.of(valid(TODAY.plusDays(31))), false, TODAY));
        assertEquals(TrainingRequirementRules.EXPIRING, TrainingRequirementRules.status(List.of(valid(TODAY.plusDays(30))), false, TODAY));
        assertEquals(TrainingRequirementRules.EXPIRING, TrainingRequirementRules.status(List.of(valid(TODAY)), false, TODAY));
    }

    @Test
    void theNewestCertificateDecides() {
        List<Held> held = List.of(valid(TODAY.minusDays(5)), valid(TODAY.plusDays(200)));
        assertEquals(TrainingRequirementRules.OK, TrainingRequirementRules.status(held, false, TODAY));
        // A renewed certificate wins over one about to lapse.
        assertEquals(TrainingRequirementRules.OK, TrainingRequirementRules.status(List.of(valid(TODAY.plusDays(3)), valid(null)), false, TODAY));
    }

    @Test
    void aLapsedCertificateIsExpiredEvenBeforeTheMorningSweepMarksIt() {
        assertEquals(TrainingRequirementRules.EXPIRED, TrainingRequirementRules.status(List.of(valid(TODAY.minusDays(1))), false, TODAY));
        assertEquals(TrainingRequirementRules.EXPIRED, TrainingRequirementRules.status(List.of(new Held("EXPIRED", TODAY.minusDays(40))), false, TODAY));
    }

    @Test
    void aWithdrawnCertificateNeverCounts() {
        assertEquals(TrainingRequirementRules.MISSING, TrainingRequirementRules.status(List.of(new Held("REVOKED", TODAY.plusDays(300))), false, TODAY));
        assertEquals(TrainingRequirementRules.EXPIRED, TrainingRequirementRules.status(List.of(new Held("REVOKED", null), new Held("EXPIRED", TODAY.minusDays(2))), false, TODAY));
    }

    @Test
    void nothingHeldIsMissing() {
        assertEquals(TrainingRequirementRules.MISSING, TrainingRequirementRules.status(List.of(), false, TODAY));
    }

    @Test
    void aBookingOnAComingSessionShowsAsBookedOnlyWhenNothingValidIsHeld() {
        assertEquals(TrainingRequirementRules.BOOKED, TrainingRequirementRules.status(List.of(), true, TODAY));
        assertEquals(TrainingRequirementRules.BOOKED, TrainingRequirementRules.status(List.of(valid(TODAY.minusDays(1))), true, TODAY));
        assertEquals(TrainingRequirementRules.OK, TrainingRequirementRules.status(List.of(valid(TODAY.plusDays(90))), true, TODAY));
        assertEquals(TrainingRequirementRules.EXPIRING, TrainingRequirementRules.status(List.of(valid(TODAY.plusDays(10))), true, TODAY));
    }

    @Test
    void onlyMissingAndExpiredNeedAction() {
        assertTrue(TrainingRequirementRules.needsAction(TrainingRequirementRules.MISSING));
        assertTrue(TrainingRequirementRules.needsAction(TrainingRequirementRules.EXPIRED));
        assertFalse(TrainingRequirementRules.needsAction(TrainingRequirementRules.BOOKED));
        assertFalse(TrainingRequirementRules.needsAction(TrainingRequirementRules.EXPIRING));
        assertFalse(TrainingRequirementRules.needsAction(TrainingRequirementRules.OK));
    }

    @Test
    void showsTheLatestExpiryAmongCertificatesNotWithdrawn() {
        assertEquals(TODAY.plusDays(9), TrainingRequirementRules.shownExpiry(List.of(valid(TODAY.plusDays(9)), valid(TODAY.minusDays(100)), new Held("REVOKED", TODAY.plusDays(500)))));
        assertNull(TrainingRequirementRules.shownExpiry(List.of(valid(null))));
    }

    @Test
    void refusesARequirementForACourseThatCannotGiveACertificate() {
        assertNotNull(TrainingRequirementRules.problem(false, false, false, null, null));
        assertNotNull(TrainingRequirementRules.problem(true, false, true, null, null));
        assertTrue(TrainingRequirementRules.problem(true, true, false, null, null).contains("certificate"));
        assertNull(TrainingRequirementRules.problem(true, true, true, "Driver", null));
        assertNotNull(TrainingRequirementRules.problem(true, true, true, "x".repeat(151), null));
    }

    @Test
    void saysWhoARequirementIsFor() {
        assertEquals("Everyone", TrainingRequirementRules.appliesTo(null, " "));
        assertEquals("Driver", TrainingRequirementRules.appliesTo(" Driver ", null));
        assertEquals("Everyone in Logistics", TrainingRequirementRules.appliesTo(null, "Logistics"));
        assertEquals("Driver in Logistics", TrainingRequirementRules.appliesTo("Driver", "Logistics"));
    }

    @Test
    void keepsTheCapitalsAsTypedWhenStoring() {
        assertEquals("Forklift Operator", TrainingRequirementRules.clean("  Forklift   Operator "));
        assertNull(TrainingRequirementRules.clean("   "));
    }
}

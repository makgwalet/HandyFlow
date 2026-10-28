package za.co.handyflow.platform.hr.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RelativeRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private static RelativeRules.Details kin(String name, String relation, String phone) {
        return RelativeRules.clean("NEXT_OF_KIN", name, relation, phone, null, null, null, false, null, null);
    }

    private static RelativeRules.Details dependant(String name, String relation, LocalDate dob) {
        return RelativeRules.clean("DEPENDANT", name, relation, null, null, dob, null, false, null, null);
    }

    @Test
    void aCompleteNextOfKinPasses() {
        assertNull(RelativeRules.problem(kin("Thandi Dlamini", "spouse", "082 555 0101"), TODAY, 0));
    }

    @Test
    void textIsTrimmedAndBlanksBecomeNull() {
        RelativeRules.Details d = RelativeRules.clean(" NEXT_OF_KIN ", "  Thandi  ", " spouse ", " ", "", null, "", null, new BigDecimal("50"), " ");
        assertEquals("Thandi", d.fullName());
        assertEquals("SPOUSE", d.relationship());
        assertNull(d.phone());
        assertNull(d.email());
        assertNull(d.notes());
        assertFalse(d.beneficiary());
        assertNull(d.beneficiaryPercent());
    }

    @Test
    void nextOfKinNeedsANameARelationshipAndAPhone() {
        assertTrue(RelativeRules.problem(kin(null, "SPOUSE", "0825550101"), TODAY, 0).contains("full name"));
        assertTrue(RelativeRules.problem(kin("Thandi", "NEIGHBOUR", "0825550101"), TODAY, 0).contains("related"));
        assertTrue(RelativeRules.problem(kin("Thandi", "SPOUSE", null), TODAY, 0).contains("phone"));
    }

    @Test
    void aDependantNeedsADateOfBirthThatIsNotInTheFuture() {
        assertTrue(RelativeRules.problem(dependant("Sipho", "CHILD", null), TODAY, 0).contains("date of birth"));
        assertTrue(RelativeRules.problem(dependant("Sipho", "CHILD", TODAY.plusDays(1)), TODAY, 0).contains("future"));
        assertNull(RelativeRules.problem(dependant("Sipho", "CHILD", TODAY), TODAY, 0));
    }

    @Test
    void theKindMustBeOneWeKnow() {
        RelativeRules.Details d = RelativeRules.clean("OTHER", "A", "SPOUSE", "0825550101", null, null, null, null, null, null);
        assertTrue(RelativeRules.problem(d, TODAY, 0).contains("next of kin or dependant"));
    }

    @Test
    void thereIsALimitPerEmployee() {
        assertTrue(RelativeRules.problem(kin("Thandi", "SPOUSE", "0825550101"), TODAY, 20).contains("20"));
        assertNull(RelativeRules.problem(kin("Thandi", "SPOUSE", "0825550101"), TODAY, 19));
    }

    @Test
    void aShareMustBeBetweenZeroAndAHundred() {
        RelativeRules.Details over = RelativeRules.clean("DEPENDANT", "S", "CHILD", null, null, TODAY.minusYears(3), null, true, new BigDecimal("101"), null);
        assertTrue(RelativeRules.problem(over, TODAY, 0).contains("between 0 and 100"));
        RelativeRules.Details neg = RelativeRules.clean("DEPENDANT", "S", "CHILD", null, null, TODAY.minusYears(3), null, true, new BigDecimal("-1"), null);
        assertTrue(RelativeRules.problem(neg, TODAY, 0).contains("between 0 and 100"));
    }

    @Test
    void sharesCannotAddUpToMoreThanTheWhole() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        List<RelativeRules.Share> others = List.of(new RelativeRules.Share(a, true, new BigDecimal("60")), new RelativeRules.Share(b, false, null));
        RelativeRules.Details forty = RelativeRules.clean("DEPENDANT", "S", "CHILD", null, null, TODAY.minusYears(3), null, true, new BigDecimal("40"), null);
        RelativeRules.Details fortyOne = RelativeRules.clean("DEPENDANT", "S", "CHILD", null, null, TODAY.minusYears(3), null, true, new BigDecimal("41"), null);
        assertNull(RelativeRules.sharesProblem(others, null, forty));
        assertTrue(RelativeRules.sharesProblem(others, null, fortyOne).contains("101%"));
    }

    @Test
    void editingALineDoesNotCountItsOldShareTwice() {
        UUID a = UUID.randomUUID();
        List<RelativeRules.Share> others = List.of(new RelativeRules.Share(a, true, new BigDecimal("60")));
        RelativeRules.Details seventy = RelativeRules.clean("DEPENDANT", "S", "CHILD", null, null, TODAY.minusYears(3), null, true, new BigDecimal("70"), null);
        assertNull(RelativeRules.sharesProblem(others, a, seventy));
        assertNotNull(RelativeRules.sharesProblem(others, UUID.randomUUID(), seventy));
    }

    @Test
    void ageIsWholeYears() {
        assertEquals(Integer.valueOf(2), RelativeRules.age(LocalDate.of(2023, 10, 29), TODAY));
        assertEquals(Integer.valueOf(3), RelativeRules.age(LocalDate.of(2023, 10, 28), TODAY));
        assertNull(RelativeRules.age(null, TODAY));
        assertNull(RelativeRules.age(TODAY.plusDays(1), TODAY));
    }
}

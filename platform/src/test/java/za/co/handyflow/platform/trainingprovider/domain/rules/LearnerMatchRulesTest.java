package za.co.handyflow.platform.trainingprovider.domain.rules;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerMatchRules.Match;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerMatchRules.Pair;
import za.co.handyflow.platform.trainingprovider.domain.rules.LearnerMatchRules.Person;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LearnerMatchRulesTest {

    private static Person p(String name, String id, LocalDate dob, String email, String phone) {
        return new Person(UUID.randomUUID(), name, id == null ? null : "PASSPORT", id, dob, email, phone);
    }

    @Test
    void sameIdentityNumberIsStrong() {
        Match m = LearnerMatchRules.compare(p("Thandi Nkosi", "A1234567", null, null, null), p("T Nkosi", "a 1234567", null, null, null));
        assertEquals("STRONG", m.strength());
        assertTrue(m.reasons().contains("ID_NUMBER"));
    }

    @Test
    void nameInAnyOrderPlusBirthDateIsStrong() {
        LocalDate d = LocalDate.of(1990, 5, 1);
        Match m = LearnerMatchRules.compare(p("Thandi Nkosi", null, d, null, null), p("NKOSI, Thandi", null, d, null, null));
        assertEquals("STRONG", m.strength());
    }

    @Test
    void aNameAloneIsOnlyPossible() {
        Match m = LearnerMatchRules.compare(p("Thandi Nkosi", null, null, null, null), p("Thandi Nkosi", null, null, null, null));
        assertEquals("POSSIBLE", m.strength());
    }

    @Test
    void emailAndPhoneTogetherAreStrongAndPhoneFormatsAgree() {
        Match m = LearnerMatchRules.compare(p("A B", null, null, "x@y.co.za", "+27 82 123 4567"), p("C D", null, null, "X@Y.co.za", "0821234567"));
        assertEquals("STRONG", m.strength());
        assertTrue(m.reasons().contains("PHONE"));
    }

    @Test
    void aSharedEmailAloneIsOnlyPossible() {
        Match m = LearnerMatchRules.compare(p("A B", null, null, "office@firm.co.za", null), p("C D", null, null, "office@firm.co.za", null));
        assertEquals("POSSIBLE", m.strength());
    }

    @Test
    void differingIdentityNumbersDowngradeAStrongNameMatch() {
        LocalDate d = LocalDate.of(1990, 5, 1);
        Match m = LearnerMatchRules.compare(p("Thandi Nkosi", "A1111111", d, null, null), p("Thandi Nkosi", "B2222222", d, null, null));
        assertEquals("POSSIBLE", m.strength());
        assertFalse(m.conflicts().isEmpty());
    }

    @Test
    void aBirthDateAloneIsNotAMatch() {
        LocalDate d = LocalDate.of(1990, 5, 1);
        assertFalse(LearnerMatchRules.compare(p("A B", null, d, null, null), p("C D", null, d, null, null)).found());
    }

    @Test
    void nothingInCommonIsNoMatch() {
        assertFalse(LearnerMatchRules.compare(p("A B", "X1", null, "a@b.c", "0821111111"), p("C D", "Y2", null, "e@f.g", "0832222222")).found());
    }

    @Test
    void candidatePairsFindsEachPairOnceAndStrongFirst() {
        Person a = p("Thandi Nkosi", "A1234567", null, null, null);
        Person b = p("Thandi Nkosi", "A1234567", null, "t@x.co", null);
        Person c = p("Thandi Nkosi", null, null, null, null);
        Person z = p("Other Person", "Z9", null, null, null);
        List<Pair> pairs = LearnerMatchRules.candidatePairs(List.of(c, a, z, b));
        assertEquals(3, pairs.size());
        assertEquals("STRONG", pairs.get(0).match().strength());
    }

    @Test
    void linkConflictOnlyWhenBothHaveDifferentNumbers() {
        assertNotNull(LearnerMatchRules.linkConflict("A1", "B2"));
        assertNull(LearnerMatchRules.linkConflict("A1", "a 1"));
        assertNull(LearnerMatchRules.linkConflict("A1", null));
        assertNull(LearnerMatchRules.linkConflict(null, "B2"));
    }

    @Test
    void proposalNeedsLiveDifferentLearnersAReasonAndAcceptedConflicts() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertNull(LearnerMatchRules.proposalProblem(a, b, "ACTIVE", "ACTIVE", null, null, List.of(), false, "Same ID document on both"));
        assertNotNull(LearnerMatchRules.proposalProblem(a, a, "ACTIVE", "ACTIVE", null, null, List.of(), false, "Same ID document on both"));
        assertNotNull(LearnerMatchRules.proposalProblem(a, b, "ARCHIVED", "ACTIVE", null, null, List.of(), false, "Same ID document on both"));
        assertNotNull(LearnerMatchRules.proposalProblem(a, b, "ACTIVE", "ARCHIVED", null, null, List.of(), false, "Same ID document on both"));
        assertNotNull(LearnerMatchRules.proposalProblem(a, b, "ACTIVE", "ACTIVE", null, null, List.of("The dates of birth differ"), false, "Same ID document on both"));
        assertNull(LearnerMatchRules.proposalProblem(a, b, "ACTIVE", "ACTIVE", null, null, List.of("The dates of birth differ"), true, "Same ID document on both"));
        assertNotNull(LearnerMatchRules.proposalProblem(a, b, "ACTIVE", "ACTIVE", null, null, List.of(), false, "short"));
    }

    @Test
    void aSecondPersonDecidesOrTheSamePersonSaysWhyAlone() {
        UUID me = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertNull(LearnerMatchRules.decisionProblem("PROPOSED", me, other, false, null, "Checked both records"));
        assertNotNull(LearnerMatchRules.decisionProblem("PROPOSED", me, me, false, null, "Checked both records"));
        assertNotNull(LearnerMatchRules.decisionProblem("PROPOSED", me, me, true, "short", "Checked both records"));
        assertNull(LearnerMatchRules.decisionProblem("PROPOSED", me, me, true, "No one else is available today", "Checked both records"));
        assertNotNull(LearnerMatchRules.decisionProblem("COMPLETED", me, other, false, null, "Checked both records"));
        assertNotNull(LearnerMatchRules.decisionProblem("PROPOSED", me, other, false, null, "no"));
    }

    @Test
    void clashesListProgrammesBothLearnersAreOn() {
        UUID x = UUID.randomUUID();
        UUID y = UUID.randomUUID();
        assertEquals(List.of(x), LearnerMatchRules.clashes(List.of(x, y), List.of(x)));
        assertTrue(LearnerMatchRules.clashes(List.of(y), List.of(x)).isEmpty());
    }
}

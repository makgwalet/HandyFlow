package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgBirthRulesTest {

    static final LocalDate BORN = LocalDate.of(2026, 11, 20);

    private static AgBirthRules.Offspring o(String sex) { return new AgBirthRules.Offspring(sex, null, null); }

    @Test void temporaryTagIsDamTagYearAndNumber() {
        assertEquals("1234-26-01", AgBirthRules.temporaryTag("1234", BORN, 1));
        assertEquals("1234-26-12", AgBirthRules.temporaryTag(" 1234 ", BORN, 12));
        assertEquals("A7-05-03", AgBirthRules.temporaryTag("A7", LocalDate.of(2005, 3, 1), 3));
    }

    @Test void blankTagsGetNumberedTemporaryTagsInOrder() {
        List<AgBirthRules.Assigned> tags = AgBirthRules.assignTags("1234", BORN, List.of("", "  ", ""), Set.of());
        assertEquals("1234-26-01", tags.get(0).tagNumber());
        assertEquals("1234-26-02", tags.get(1).tagNumber());
        assertEquals("1234-26-03", tags.get(2).tagNumber());
        assertTrue(tags.get(0).temporary() && tags.get(1).temporary() && tags.get(2).temporary());
    }

    @Test void aTypedTagIsKeptAndIsNotTemporary() {
        List<AgBirthRules.Assigned> tags = AgBirthRules.assignTags("1234", BORN, List.of("ZA-501", ""), Set.of());
        assertEquals("ZA-501", tags.get(0).tagNumber());
        assertFalse(tags.get(0).temporary());
        assertEquals("1234-26-01", tags.get(1).tagNumber());
        assertTrue(tags.get(1).temporary());
    }

    @Test void temporaryNumbersSkipTagsAlreadyOnTheFarm() {
        List<AgBirthRules.Assigned> tags = AgBirthRules.assignTags("1234", BORN, List.of("", ""), Set.of("1234-26-01", "1234-26-03"));
        assertEquals("1234-26-02", tags.get(0).tagNumber());
        assertEquals("1234-26-04", tags.get(1).tagNumber());
    }

    @Test void aTypedTagAlreadyOnTheFarmOrRepeatedIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.assignTags("1234", BORN, List.of("X1"), Set.of("X1")));
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.assignTags("1234", BORN, List.of("X1", "X1"), Set.of()));
    }

    @Test void aTypedTagThatLooksLikeATemporaryOneIsNotGivenTwice() {
        List<AgBirthRules.Assigned> tags = AgBirthRules.assignTags("1234", BORN, List.of("1234-26-01", ""), Set.of());
        assertEquals("1234-26-01", tags.get(0).tagNumber());
        assertEquals("1234-26-02", tags.get(1).tagNumber());
    }

    @Test void aTagOverOneHundredCharactersIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.assignTags("1234", BORN, List.of("x".repeat(101)), Set.of()));
    }

    @Test void oneEntryPerLiveOffspringWithAKnownSex() {
        assertEquals(List.of("MALE", "FEMALE"), AgBirthRules.requireOffspringDetail(2, List.of(o("male"), o(" Female "))));
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.requireOffspringDetail(2, List.of(o("MALE"))));
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.requireOffspringDetail(1, List.of(o("MALE"), o("MALE"))));
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.requireOffspringDetail(1, null));
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.requireOffspringDetail(1, List.of(o("CASTRATED"))));
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.requireOffspringDetail(1, List.of(o(""))));
    }

    @Test void groupTrackedSpeciesGetNoIndividualAnimals() {
        assertFalse(AgBirthRules.createsIndividualAnimals("GROUP"));
        assertFalse(AgBirthRules.createsIndividualAnimals(" group "));
        assertTrue(AgBirthRules.createsIndividualAnimals("INDIVIDUAL"));
        assertTrue(AgBirthRules.createsIndividualAnimals("BOTH"));
        assertTrue(AgBirthRules.createsIndividualAnimals(null));
    }

    @Test void reminderDaysAreEmptyOrOneToThreeSixtyFive() {
        assertNull(AgBirthRules.requireReminderDays(null));
        assertEquals(Integer.valueOf(1), AgBirthRules.requireReminderDays(1));
        assertEquals(Integer.valueOf(365), AgBirthRules.requireReminderDays(365));
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.requireReminderDays(0));
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.requireReminderDays(366));
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.requireReminderDays(-5));
    }

    @Test void reminderIsDueOnTheDayCountedFromBirth() {
        assertFalse(AgBirthRules.reminderDue(BORN, 30, BORN.plusDays(29)));
        assertTrue(AgBirthRules.reminderDue(BORN, 30, BORN.plusDays(30)));
        assertTrue(AgBirthRules.reminderDue(BORN, 30, BORN.plusDays(90)));
        assertFalse(AgBirthRules.reminderDue(BORN, null, BORN.plusDays(90)));
        assertFalse(AgBirthRules.reminderDue(null, 30, BORN.plusDays(90)));
    }

    @Test void onlyATemporaryTagCanBeReplacedAndTheNewOneMustDiffer() {
        AgBirthRules.requireTemporaryTag(true);
        assertThrows(IllegalStateException.class, () -> AgBirthRules.requireTemporaryTag(false));
        assertEquals("ZA-501", AgBirthRules.requireNewTag("1234-26-01", " ZA-501 "));
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.requireNewTag("1234-26-01", "1234-26-01"));
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.requireNewTag("1234-26-01", "  "));
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.requireNewTag("1234-26-01", null));
        assertThrows(IllegalArgumentException.class, () -> AgBirthRules.requireNewTag("1234-26-01", "x".repeat(101)));
    }

    @Test void groupCountReasonNamesTheNumberAndDay() {
        assertEquals("Birth: 3 born alive on 2026-11-20", AgBirthRules.groupCountReason(3, BORN));
    }
}

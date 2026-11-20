package za.co.handyflow.platform.legalpractice.application.internal;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConflictMatcherTest {

    private static Optional<String> m(String a, String b) { return ConflictMatcher.compare(a, null, b, null); }

    @Test
    void sameNameIgnoringCaseAccentsPunctuationAndOrder() {
        assertEquals(Optional.of("EXACT_NAME"), m("Thabo Dlamini", "THABO  DLAMINI"));
        assertEquals(Optional.of("EXACT_NAME"), m("Thabo Dlamini", "Dlamini, Thabo"));
        assertEquals(Optional.of("EXACT_NAME"), m("José Müller", "Jose Muller"));
    }

    @Test
    void companySuffixesDoNotMakeNamesDifferent() {
        assertEquals(Optional.of("EXACT_NAME"), m("Acme Holdings (Pty) Ltd", "ACME Holdings"));
        assertEquals(Optional.of("EXACT_NAME"), m("The Dlamini Trust", "Dlamini Trust"));
    }

    @Test
    void idOrRegistrationNumberMatchesEvenWhenTheNamesDiffer() {
        assertEquals(Optional.of("EXACT_ID"), ConflictMatcher.compare("Acme Holdings", "2020/123456/07", "Acme Group", "2020 123456 07"));
        assertEquals(Optional.of("EXACT_ID"), ConflictMatcher.compare("A", "8001015009087", "B", "8001015009087"));
        assertTrue(ConflictMatcher.compare("Acme Holdings", "2020/123456/07", "Acme Holdings", "2021/999999/07").isPresent(), "names still match");
    }

    @Test
    void aBlankIdNeverMatches() {
        assertTrue(ConflictMatcher.compare("Alpha Beta", "", "Gamma Delta", " ").isEmpty());
        assertTrue(ConflictMatcher.compare("Alpha Beta", null, "Gamma Delta", null).isEmpty());
    }

    @Test
    void aLongerNameContainingAllWordsOfAShorterOneIsSimilar() {
        assertEquals(Optional.of("SIMILAR"), m("Dlamini Trading", "Dlamini Trading and Logistics (Pty) Ltd"));
        assertEquals(Optional.of("SIMILAR"), m("Thabo Sipho Dlamini", "Thabo Dlamini"));
    }

    @Test
    void mostlyTheSameWordsIsSimilar() {
        assertEquals(Optional.of("SIMILAR"), m("Cape Town Property Group", "Cape Town Property Holdings"));
    }

    @Test
    void oneSharedWordIsNotEnough() {
        assertTrue(m("Thabo Dlamini", "Sipho Dlamini").isEmpty());
        assertTrue(m("Smith", "John Smith").isEmpty(), "a single word only matches an exact name");
        assertTrue(m("Acme Holdings", "Acme Logistics").isEmpty());
        assertEquals(Optional.of("EXACT_NAME"), m("Smith", "SMITH"));
    }

    @Test
    void unrelatedNamesAndEmptyInputDoNotMatch() {
        assertTrue(m("Alpha Beta", "Gamma Delta").isEmpty());
        assertTrue(m(null, "Gamma Delta").isEmpty());
        assertTrue(m("   ", "Gamma Delta").isEmpty());
        assertTrue(m("(Pty) Ltd", "Pty Ltd").isEmpty(), "a name made only of suffixes has nothing to compare");
    }

    @Test
    void tokensAndIdNormalisation() {
        assertEquals(java.util.List.of("acme", "holdings"), ConflictMatcher.tokens("ACME Holdings (Pty) Ltd."));
        assertEquals("2020123456" + "07", ConflictMatcher.normalizeId("2020/123456/07"));
        assertEquals("", ConflictMatcher.normalizeId(null));
    }
}

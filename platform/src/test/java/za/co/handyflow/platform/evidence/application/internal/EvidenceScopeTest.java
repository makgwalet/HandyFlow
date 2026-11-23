package za.co.handyflow.platform.evidence.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceScopeTest {

    @Test void ownActiveFileMatches() {
        assertTrue(EvidenceScope.matches("ACTIVE", "agriculture", "AgAnimal", "agriculture", "AgAnimal"));
    }

    @Test void anotherModulesFileDoesNotMatch() {
        assertFalse(EvidenceScope.matches("ACTIVE", "hr", "Employee", "agriculture", "AgAnimal"));
        assertFalse(EvidenceScope.matches("ACTIVE", "expenses", "ExpenseClaim", "agriculture", "AgAnimal"));
    }

    @Test void sameKindOfRecordInAnotherModuleDoesNotMatch() {
        assertFalse(EvidenceScope.matches("ACTIVE", "livestock", "AgAnimal", "agriculture", "AgAnimal"));
    }

    @Test void otherKindOfRecordInSameModuleDoesNotMatch() {
        assertFalse(EvidenceScope.matches("ACTIVE", "agriculture", "AgGroup", "agriculture", "AgAnimal"));
    }

    @Test void detachedOrDisposedDoesNotMatch() {
        assertFalse(EvidenceScope.matches("DETACHED", "agriculture", "AgAnimal", "agriculture", "AgAnimal"));
        assertFalse(EvidenceScope.matches("DISPOSED", "agriculture", "AgAnimal", "agriculture", "AgAnimal"));
        assertFalse(EvidenceScope.matches(null, "agriculture", "AgAnimal", "agriculture", "AgAnimal"));
    }

    @Test void restrictedNeverMatches() {
        assertFalse(EvidenceScope.matches("ACTIVE", "restricted-er", "ErCase", "restricted-er", "ErCase"));
    }

    @Test void nullWantedScopeNeverMatches() {
        assertFalse(EvidenceScope.matches("ACTIVE", "agriculture", "AgAnimal", null, "AgAnimal"));
        assertFalse(EvidenceScope.matches("ACTIVE", "agriculture", "AgAnimal", "agriculture", null));
    }
}

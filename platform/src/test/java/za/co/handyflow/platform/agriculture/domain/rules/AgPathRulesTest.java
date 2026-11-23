package za.co.handyflow.platform.agriculture.domain.rules;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgPathRulesTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Test void animalUrlAcceptsABodyForThatAnimal() {
        assertDoesNotThrow(() -> AgPathRules.requireAnimal(A, A, null));
    }

    @Test void animalUrlRefusesAnotherAnimalAGroupOrNothing() {
        assertThrows(IllegalArgumentException.class, () -> AgPathRules.requireAnimal(A, B, null));
        assertThrows(IllegalArgumentException.class, () -> AgPathRules.requireAnimal(A, null, B));
        assertThrows(IllegalArgumentException.class, () -> AgPathRules.requireAnimal(A, null, null));
        assertThrows(IllegalArgumentException.class, () -> AgPathRules.requireAnimal(A, A, B), "both set");
    }

    @Test void groupUrlAcceptsABodyForThatGroup() {
        assertDoesNotThrow(() -> AgPathRules.requireGroup(A, null, A));
    }

    @Test void groupUrlRefusesAnotherGroupAnAnimalOrNothing() {
        assertThrows(IllegalArgumentException.class, () -> AgPathRules.requireGroup(A, null, B));
        assertThrows(IllegalArgumentException.class, () -> AgPathRules.requireGroup(A, B, null));
        assertThrows(IllegalArgumentException.class, () -> AgPathRules.requireGroup(A, null, null));
        assertThrows(IllegalArgumentException.class, () -> AgPathRules.requireGroup(A, B, A), "both set");
    }

    @Test void theMessageSaysWhichIdToFix() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> AgPathRules.requireAnimal(A, B, null)).getMessage().contains("animal"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> AgPathRules.requireGroup(A, null, B)).getMessage().contains("group"));
    }
}

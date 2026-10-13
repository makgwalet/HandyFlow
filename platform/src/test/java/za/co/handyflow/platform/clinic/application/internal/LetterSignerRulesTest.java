package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LetterSignerRulesTest {

    private static final UUID SOMEONE = UUID.randomUUID();

    @Test
    void noNamedSignerNeedsNoExtraPermission() {
        assertTrue(LetterSignerRules.allowed(null, List.of()));
        assertTrue(LetterSignerRules.allowed(null, null));
    }

    @Test
    void namingAPractitionerNeedsThePermission() {
        assertFalse(LetterSignerRules.allowed(SOMEONE, List.of("CLINIC_DOCUMENT_CREATE")));
        assertFalse(LetterSignerRules.allowed(SOMEONE, List.of()));
        assertFalse(LetterSignerRules.allowed(SOMEONE, null));
        assertTrue(LetterSignerRules.allowed(SOMEONE, List.of("CLINIC_DOCUMENT_CREATE", "CLINIC_DOCUMENT_SIGN_AS_OTHER")));
    }
}

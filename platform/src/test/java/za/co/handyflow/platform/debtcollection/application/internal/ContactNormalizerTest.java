package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContactNormalizerTest {

    @Test
    void southAfricanNumbersCompareEqualInAnyFormat() {
        String expected = "27821234567";
        assertEquals(expected, ContactNormalizer.normalize("082 123 4567"));
        assertEquals(expected, ContactNormalizer.normalize("+27 82 123 4567"));
        assertEquals(expected, ContactNormalizer.normalize("0821234567"));
        assertEquals(expected, ContactNormalizer.normalize("0027821234567"));
    }

    @Test
    void emailsAreTrimmedAndLowerCased() {
        assertEquals("a@b.co.za", ContactNormalizer.normalize("  A@B.co.za "));
    }

    @Test
    void blankOrDigitlessInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ContactNormalizer.normalize("  "));
        assertThrows(IllegalArgumentException.class, () -> ContactNormalizer.normalize("n/a"));
    }

    @Test
    void optOutKeywordsMatchWholeMessageOnly() {
        assertTrue(CommunicationOptOutService.isOptOutKeyword("STOP"));
        assertTrue(CommunicationOptOutService.isOptOutKeyword(" stop. "));
        assertTrue(CommunicationOptOutService.isOptOutKeyword("Opt out"));
        assertFalse(CommunicationOptOutService.isOptOutKeyword("please don't stop the plan"));
        assertFalse(CommunicationOptOutService.isOptOutKeyword("cancel"));
        assertFalse(CommunicationOptOutService.isOptOutKeyword(null));
    }
}

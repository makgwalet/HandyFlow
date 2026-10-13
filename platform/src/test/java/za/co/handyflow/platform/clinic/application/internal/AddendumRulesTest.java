package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AddendumRulesTest {
    @Test void blankMeansAPlainNote() { assertEquals("OTHER", AddendumRules.kind(null)); assertEquals("OTHER", AddendumRules.kind("  ")); }
    @Test void aChosenKindIsAccepted() { assertEquals("CORRECTION", AddendumRules.kind(" correction ")); assertEquals("RESULT", AddendumRules.kind("RESULT")); }
    @Test void theSystemKindCannotBeChosen() { assertThrows(IllegalArgumentException.class, () -> AddendumRules.kind("LATE_MEDICINE")); }
    @Test void unknownKindsAreRefused() { assertThrows(IllegalArgumentException.class, () -> AddendumRules.kind("HACK")); }
    @Test void everyKindHasAReadableLabel() {
        for (String k : AddendumRules.CHOOSABLE) assertNotEquals("", AddendumRules.label(k));
        assertEquals("Medicine added after signing", AddendumRules.label("LATE_MEDICINE"));
        assertEquals("Note", AddendumRules.label(null));
    }
    @Test void theSignerIsToldOnlyWhenSomeoneElseAdds() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertTrue(AddendumRules.tellSigner(a, b));
        assertFalse(AddendumRules.tellSigner(a, a));
        assertFalse(AddendumRules.tellSigner(null, b));
        assertTrue(AddendumRules.tellSigner(a, null));
    }
    @Test void theNoticeNamesWhoWroteAndStaysShort() {
        assertEquals("Dr Dlamini wrote: Rash settled", AddendumRules.noticeDetail("Dr Dlamini", " Rash   settled "));
        assertTrue(AddendumRules.noticeDetail(null, "x").startsWith("Someone wrote"));
        assertEquals(300, AddendumRules.noticeDetail("A", "y".repeat(2000)).length());
        assertTrue(AddendumRules.noticeTitle("CORRECTION").endsWith("Correction"));
    }
}

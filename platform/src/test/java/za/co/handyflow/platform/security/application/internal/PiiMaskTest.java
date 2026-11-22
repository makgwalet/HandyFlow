package za.co.handyflow.platform.security.application.internal;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PiiMaskTest {
    @Test void saIdShowsBirthDatePartOnly() {
        assertEquals("850101*******", PiiMask.maskId("8501015026088"));
    }
    @Test void passportShowsLastThreeOnly() {
        assertEquals("******789", PiiMask.maskId("A12345789"));
    }
    @Test void shortOrEmptyValuesAreHandled() {
        assertNull(PiiMask.maskId(null));
        assertEquals("", PiiMask.maskId(""));
        assertEquals("**", PiiMask.maskId("AB"));
    }
    @Test void accountShowsLastFourOnly() {
        String m = PiiMask.maskAccount("62012345678");
        assertTrue(m.endsWith("5678"));
        assertEquals(11, m.length());
        assertFalse(m.contains("6201"));
        assertEquals("•••", PiiMask.maskAccount("123"));
        assertNull(PiiMask.maskAccount(null));
    }
    @Test void maskedValuesAreRecognised() {
        assertTrue(PiiMask.isMasked("850101*******"));
        assertTrue(PiiMask.isMasked(PiiMask.maskAccount("62012345678")));
        assertFalse(PiiMask.isMasked("8501015026088"));
        assertFalse(PiiMask.isMasked(null));
    }
    @Test void maskedOrBlankIncomingKeepsStoredValue() {
        assertEquals("8501015026088", PiiMask.keepIfMasked("850101*******", "8501015026088"));
        assertEquals("8501015026088", PiiMask.keepIfMasked("", "8501015026088"));
        assertEquals("8501015026088", PiiMask.keepIfMasked(null, "8501015026088"));
        assertEquals("9001015026088", PiiMask.keepIfMasked("9001015026088", "8501015026088"));
    }
    @Test void aMaskedValueNeverRoundTripsToTheOriginal() {
        // the mask must not let someone recover the original from what the API returns
        assertNotEquals("8501015026088", PiiMask.maskId("8501015026088"));
        assertNotEquals("62012345678", PiiMask.maskAccount("62012345678"));
    }
}

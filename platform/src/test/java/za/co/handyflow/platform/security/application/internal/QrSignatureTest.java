package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QrSignatureTest {
    @Test void roundTrip() throws Exception {
        String sig = QrSignature.sign("secret", "cp:site");
        assertTrue(QrSignature.matches("secret", "cp:site", sig));
    }
    @Test void wrongSecretPayloadOrSignatureFails() throws Exception {
        String sig = QrSignature.sign("secret", "cp:site");
        assertFalse(QrSignature.matches("other", "cp:site", sig));
        assertFalse(QrSignature.matches("secret", "cp:other", sig));
        assertFalse(QrSignature.matches("secret", "cp:site", sig + "x"));
    }
    @Test void missingSecretOrSignatureFailsInsteadOfThrowing() {
        assertFalse(QrSignature.matches(null, "p", "s"));
        assertFalse(QrSignature.matches(" ", "p", "s"));
        assertFalse(QrSignature.matches("secret", "p", null));
    }
    @Test void matchesThePayloadFormatAlreadyPrinted() throws Exception {
        // Same algorithm as generateQrPayload: Base64url, no padding, HMAC-SHA256.
        String sig = QrSignature.sign("k", "a:b");
        assertFalse(sig.contains("=")); assertFalse(sig.contains("+")); assertFalse(sig.contains("/"));
        assertEquals(43, sig.length());
    }
}

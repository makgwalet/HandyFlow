package za.co.handyflow.platform.projects.application.internal.document;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RecipientAccessRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");

    @Test
    void secrets_areLongUniqueAndInTheExpectedFormat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String s = RecipientAccessRules.newSecret();
            assertEquals(43, s.length());
            assertTrue(RecipientAccessRules.secretFormatOk(s), s);
            assertTrue(seen.add(s), "duplicate secret");
        }
    }

    @Test
    void secretFormat_refusesAnythingElse() {
        assertFalse(RecipientAccessRules.secretFormatOk(null));
        assertFalse(RecipientAccessRules.secretFormatOk(""));
        assertFalse(RecipientAccessRules.secretFormatOk("short"));
        assertFalse(RecipientAccessRules.secretFormatOk("a".repeat(42)));
        assertFalse(RecipientAccessRules.secretFormatOk("a".repeat(44)));
        assertFalse(RecipientAccessRules.secretFormatOk("a".repeat(42) + "'"));
        assertFalse(RecipientAccessRules.secretFormatOk("a".repeat(42) + " "));
        assertFalse(RecipientAccessRules.secretFormatOk("a".repeat(41) + "%2e"));
        assertTrue(RecipientAccessRules.secretFormatOk("A".repeat(21) + "-" + "_".repeat(21)));
    }

    @Test
    void hashing_isStable_hexAndNotTheSecret() {
        String s = RecipientAccessRules.newSecret();
        String h = RecipientAccessRules.hashSecret(s);
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]{64}"));
        assertNotEquals(s, h);
        assertEquals(h, RecipientAccessRules.hashSecret(s));
        assertNotEquals(h, RecipientAccessRules.hashSecret(RecipientAccessRules.newSecret()));
    }

    @Test
    void otp_isSixDigits() {
        for (int i = 0; i < 500; i++) {
            String c = RecipientAccessRules.newOtp();
            assertEquals(6, c.length());
            assertTrue(RecipientAccessRules.otpFormatOk(c), c);
        }
        assertTrue(RecipientAccessRules.otpFormatOk(" 012345 "));
        assertFalse(RecipientAccessRules.otpFormatOk("12345"));
        assertFalse(RecipientAccessRules.otpFormatOk("1234567"));
        assertFalse(RecipientAccessRules.otpFormatOk("12a456"));
        assertFalse(RecipientAccessRules.otpFormatOk(null));
    }

    @Test
    void otpHash_isTiedToTheRecipient() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        assertEquals(RecipientAccessRules.hashOtp(a, "123456"), RecipientAccessRules.hashOtp(a, " 123456 "));
        assertNotEquals(RecipientAccessRules.hashOtp(a, "123456"), RecipientAccessRules.hashOtp(b, "123456"));
        assertNotEquals(RecipientAccessRules.hashOtp(a, "123456"), RecipientAccessRules.hashOtp(a, "123457"));
    }

    @Test
    void hashesEqual_handlesNullAndMismatch() {
        assertTrue(RecipientAccessRules.hashesEqual("abc", "abc"));
        assertFalse(RecipientAccessRules.hashesEqual("abc", "abd"));
        assertFalse(RecipientAccessRules.hashesEqual("abc", null));
        assertFalse(RecipientAccessRules.hashesEqual(null, null));
    }

    @Test
    void otpLocksAfterFiveWrongGuesses() {
        assertFalse(RecipientAccessRules.otpLocked(0));
        assertFalse(RecipientAccessRules.otpLocked(4));
        assertTrue(RecipientAccessRules.otpLocked(5));
        assertTrue(RecipientAccessRules.otpLocked(9));
    }

    @Test
    void resendIsLimitedToOnceAMinute() {
        assertTrue(RecipientAccessRules.mayResendOtp(null, NOW));
        assertFalse(RecipientAccessRules.mayResendOtp(NOW.minusSeconds(10), NOW));
        assertFalse(RecipientAccessRules.mayResendOtp(NOW.minusSeconds(59), NOW));
        assertTrue(RecipientAccessRules.mayResendOtp(NOW.minusSeconds(60), NOW));
        assertTrue(RecipientAccessRules.mayResendOtp(NOW.minusSeconds(600), NOW));
    }

    @Test
    void expiryDays_defaultAndClamped() {
        assertEquals(14, RecipientAccessRules.clampExpiryDays(null));
        assertEquals(1, RecipientAccessRules.clampExpiryDays(0));
        assertEquals(1, RecipientAccessRules.clampExpiryDays(-5));
        assertEquals(30, RecipientAccessRules.clampExpiryDays(30));
        assertEquals(90, RecipientAccessRules.clampExpiryDays(500));
    }

    @Test
    void link_worksOnlyBeforeExpiryAndUntilRevoked() {
        assertTrue(RecipientAccessRules.linkUsable(NOW.plusSeconds(1), null, NOW));
        assertFalse(RecipientAccessRules.linkUsable(NOW, null, NOW));                   // expires exactly now: closed
        assertFalse(RecipientAccessRules.linkUsable(NOW.minusSeconds(1), null, NOW));
        assertFalse(RecipientAccessRules.linkUsable(NOW.plusSeconds(1000), NOW.minusSeconds(1), NOW));   // revoked
        assertFalse(RecipientAccessRules.linkUsable(null, null, NOW));
    }

    @Test
    void session_worksOnlyBeforeItExpires() {
        assertTrue(RecipientAccessRules.sessionUsable(NOW.plusSeconds(1), NOW));
        assertFalse(RecipientAccessRules.sessionUsable(NOW, NOW));
        assertFalse(RecipientAccessRules.sessionUsable(null, NOW));
    }

    @Test
    void maskEmail_hidesTheName() {
        assertEquals("j*******@example.com", RecipientAccessRules.maskEmail("jane.doe@example.com"));
        assertEquals("a*@x.co", RecipientAccessRules.maskEmail("ab@x.co"));
        assertEquals("a*@x.co", RecipientAccessRules.maskEmail("a@x.co"));
        assertEquals("***", RecipientAccessRules.maskEmail("nonsense"));
        assertEquals("", RecipientAccessRules.maskEmail(null));
    }

    @Test
    void normaliseEmail_trimsAndLowerCases() {
        assertEquals("jane@example.com", RecipientAccessRules.normaliseEmail("  Jane@Example.COM "));
        assertNull(RecipientAccessRules.normaliseEmail(null));
    }
}

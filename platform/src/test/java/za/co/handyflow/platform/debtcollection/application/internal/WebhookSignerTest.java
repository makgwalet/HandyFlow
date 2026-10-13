package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignerTest {

    // Vectors computed independently (Python hmac/hashlib), not with this code.
    private static final String SIGNATURE = "v1=1698a50bc74d1ff1db85c4e0a5297c2ad9fdba245d5737cdb789e4cc6e098940";
    private static final String MASTER = "0123456789abcdef0123456789abcdef";
    private static final UUID ENDPOINT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final byte[] BODY = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

    @Test
    void signMatchesIndependentVector() {
        assertEquals(SIGNATURE, WebhookSigner.sign("s3cret", "1700000000", BODY));
    }

    @Test
    void verifyAcceptsAValidFreshSignature() {
        assertTrue(WebhookSigner.verify("s3cret", "1700000000", BODY, SIGNATURE, 1700000100L, 300));
        assertTrue(WebhookSigner.verify("s3cret", "1700000000", BODY, SIGNATURE.toUpperCase().replace("V1=", "v1="), 1700000100L, 300));
    }

    @Test
    void verifyRejectsStaleOrFutureTimestamps() {
        assertFalse(WebhookSigner.verify("s3cret", "1700000000", BODY, SIGNATURE, 1700000301L, 300));
        assertFalse(WebhookSigner.verify("s3cret", "1700000000", BODY, SIGNATURE, 1699999699L, 300));
    }

    @Test
    void verifyRejectsTamperingWrongSecretAndGarbage() {
        assertFalse(WebhookSigner.verify("s3cret", "1700000000", "{\"a\":2}".getBytes(StandardCharsets.UTF_8), SIGNATURE, 1700000100L, 300));
        assertFalse(WebhookSigner.verify("other", "1700000000", BODY, SIGNATURE, 1700000100L, 300));
        assertFalse(WebhookSigner.verify("s3cret", "not-a-number", BODY, SIGNATURE, 1700000100L, 300));
        assertFalse(WebhookSigner.verify("s3cret", null, BODY, SIGNATURE, 1700000100L, 300));
        assertFalse(WebhookSigner.verify("s3cret", "1700000000", BODY, null, 1700000100L, 300));
        assertFalse(WebhookSigner.verify("s3cret", "1700000000", BODY, "v1=deadbeef", 1700000100L, 300));
    }

    @Test
    void timestampIsPartOfTheSignedData() {
        assertNotEquals(WebhookSigner.sign("s3cret", "1700000000", BODY), WebhookSigner.sign("s3cret", "1700000001", BODY));
    }

    @Test
    void derivedSecretMatchesIndependentVectorAndChangesWithVersion() {
        assertEquals("e70f1611094b1801519f7c9b07cfa6a4dd721c2f7bddffe7759bf2fa3063b4db", WebhookSigner.deriveSecret(MASTER, ENDPOINT, 1));
        assertEquals("f1caca816732fbbfb61a8816a4ef52b2a8bc14bde787aa8899e03f67624b8d70", WebhookSigner.deriveSecret(MASTER, ENDPOINT, 2));
    }

    @Test
    void sha256KnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", WebhookSigner.sha256Hex("abc"));
    }
}

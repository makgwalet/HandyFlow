package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedValueTest {

    @Test
    void wrapAddsThePrefixAndUnwrapRemovesIt() {
        String stored = EncryptedValue.wrap("abc123");
        assertEquals("enc1:abc123", stored);
        assertTrue(EncryptedValue.isEncrypted(stored));
        assertEquals("abc123", EncryptedValue.unwrap(stored));
    }

    @Test
    void aPlainValueIsNotTakenForEncrypted() {
        assertFalse(EncryptedValue.isEncrypted("8001015009087"));
        assertFalse(EncryptedValue.isEncrypted(""));
        assertFalse(EncryptedValue.isEncrypted(null));
    }

    @Test
    void nullStaysNull() {
        assertNull(EncryptedValue.wrap(null));
    }
}

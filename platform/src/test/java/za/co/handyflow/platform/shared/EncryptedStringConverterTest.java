package za.co.handyflow.platform.shared;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EncryptedStringConverterTest {

    private final FieldEncryptionService service = new FieldEncryptionService(
            Base64.getEncoder().encodeToString(new byte[32]));
    private final EncryptedStringConverter converter = new EncryptedStringConverter(service);

    @Test
    void aValueIsStoredEncryptedAndReadBackUnchanged() {
        String stored = converter.convertToDatabaseColumn("62001234567");
        assertTrue(stored.startsWith("enc1:"));
        assertFalse(stored.contains("62001234567"));
        assertEquals("62001234567", converter.convertToEntityAttribute(stored));
    }

    @Test
    void aValueSavedBeforeEncryptionStillReads() {
        assertEquals("8001015009087", converter.convertToEntityAttribute("8001015009087"));
    }

    @Test
    void nullGoesInAndOutAsNull() {
        assertNull(converter.convertToDatabaseColumn(null));
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void theSameValueEncryptsDifferentlyEachTime() {
        assertNotEquals(converter.convertToDatabaseColumn("123"), converter.convertToDatabaseColumn("123"));
    }

    @Test
    void aTamperedValueFailsInsteadOfReturningGarbage() {
        String stored = converter.convertToDatabaseColumn("123");
        String tampered = stored.substring(0, stored.length() - 4) + "AAAA";
        assertThrows(HandyFlowException.class, () -> converter.convertToEntityAttribute(tampered));
    }
}

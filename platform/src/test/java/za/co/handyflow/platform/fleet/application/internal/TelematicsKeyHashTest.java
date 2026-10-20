package za.co.handyflow.platform.fleet.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Ingest keys are stored only as a SHA-256 hash, so the hash must be stable and must not leak or collide on look-alike keys. */
class TelematicsKeyHashTest {
    @Test void theHashIsSixtyFourLowercaseHexCharactersAndStable() {
        String h = TelematicsIngestService.hashKey("abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", h); // SHA-256 of "abc"
        assertEquals(h, TelematicsIngestService.hashKey("abc"));
    }

    @Test void differentKeysGiveDifferentHashesAndTheHashIsNotTheKey() {
        assertNotEquals(TelematicsIngestService.hashKey("key-1"), TelematicsIngestService.hashKey("key-2"));
        assertNotEquals(TelematicsIngestService.hashKey("Key-1"), TelematicsIngestService.hashKey("key-1"));
        assertFalse(TelematicsIngestService.hashKey("key-1").contains("key-1"));
    }
}

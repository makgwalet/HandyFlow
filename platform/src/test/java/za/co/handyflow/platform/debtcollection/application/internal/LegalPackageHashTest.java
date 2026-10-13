package za.co.handyflow.platform.debtcollection.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LegalPackageHashTest {

    @Test
    void sha256MatchesKnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                LegalHandoverPackageBuilder.sha256("abc"));
    }

    @Test
    void anyChangeToThePackageChangesTheHash() {
        assertNotEquals(LegalHandoverPackageBuilder.sha256("{\"a\":1}"), LegalHandoverPackageBuilder.sha256("{\"a\":2}"));
    }
}

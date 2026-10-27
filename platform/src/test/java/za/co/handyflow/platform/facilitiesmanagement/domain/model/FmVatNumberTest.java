package za.co.handyflow.platform.facilitiesmanagement.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FmVatNumberTest {

    @Test
    void aValidNumberIsAcceptedAsIs() {
        assertEquals("4123456789", FmVatNumber.normalize("4123456789"));
    }

    @Test
    void spacesAndHyphensAreStripped() {
        assertEquals("4123456789", FmVatNumber.normalize(" 4123 456 789 "));
        assertEquals("4123456789", FmVatNumber.normalize("412-345-6789"));
    }

    @Test
    void blankOrMissingMeansNotSupplied() {
        assertNull(FmVatNumber.normalize(null));
        assertNull(FmVatNumber.normalize(""));
        assertNull(FmVatNumber.normalize("   "));
    }

    @Test
    void aNumberThatDoesNotStartWithFourIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FmVatNumber.normalize("3123456789"));
    }

    @Test
    void theWrongLengthIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FmVatNumber.normalize("412345678"));
        assertThrows(IllegalArgumentException.class, () -> FmVatNumber.normalize("41234567890"));
    }

    @Test
    void lettersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> FmVatNumber.normalize("4123ABC789"));
    }

    @Test
    void aClientKeepsItsNumberUntilTold() {
        FmClient client = FmClient.create(za.co.handyflow.platform.shared.TenantId.generate(), "CLI-0001", "Acme",
                null, null, null, null, null);
        client.changeVatNumber("4123456789");
        assertEquals("4123456789", client.getVatNumber());
        client.changeVatNumber(null);          // not supplied: unchanged
        assertEquals("4123456789", client.getVatNumber());
        client.changeVatNumber("");            // blank: cleared
        assertNull(client.getVatNumber());
        assertThrows(IllegalArgumentException.class, () -> client.changeVatNumber("12"));
    }

    @Test
    void aVatRegisteredProfileMustHaveAValidNumber() {
        FmProfile profile = FmProfile.create(za.co.handyflow.platform.shared.TenantId.generate(), "Acme FM", null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> profile.updateBillingSettings(
                true, "123", null, null, null, null, "2300", null));
        profile.updateBillingSettings(true, "4123 456 789", null, null, null, null, "2300", null);
        assertEquals("4123456789", profile.getVatNumber());
        assertTrue(profile.isVatRegistered());
    }

    @Test
    void aVatRegisteredProfileNeedsAVatLedgerAccountToo() {
        FmProfile profile = FmProfile.create(za.co.handyflow.platform.shared.TenantId.generate(), "Acme FM", null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> profile.updateBillingSettings(
                true, "4123456789", null, null, null, null, null, null));
    }

    @Test
    void aProfileAddressIsKeptUntilTold() {
        FmProfile profile = FmProfile.create(za.co.handyflow.platform.shared.TenantId.generate(), "Acme FM", null, null, null, null);
        profile.changeAddress("9 Supplier St, Pretoria");
        assertEquals("9 Supplier St, Pretoria", profile.getAddress());
        profile.changeAddress(null);
        assertEquals("9 Supplier St, Pretoria", profile.getAddress());
        profile.changeAddress("  ");
        assertNull(profile.getAddress());
    }
}

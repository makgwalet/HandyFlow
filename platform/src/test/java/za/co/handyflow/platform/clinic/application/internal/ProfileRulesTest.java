package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.dto.ProfileDtos.*;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ProfileRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    private static ProfileRequest req(String idType, String contact, String postal, String payer) {
        return new ProfileRequest(" Mr ", idType, null, "  isiZulu ", contact, "12 Main Rd", null, "Sandton", "Johannesburg", "Gauteng", postal, null, null, null, null, payer);
    }

    @Test
    void profileDetailsAreTrimmedAndCodesNormalised() {
        var c = ProfileRules.clean(req("sa id", "whatsapp", "2196", "self pay"));
        assertEquals("Mr", c.title());
        assertEquals("SA_ID", c.idType());
        assertEquals("WHATSAPP", c.preferredContact());
        assertEquals("SELF_PAY", c.paymentType());
        assertEquals("isiZulu", c.preferredLanguage());
        assertNull(c.addressLine2());
    }

    @Test
    void badCodesAndPostalCodesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> ProfileRules.clean(req("licence", null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> ProfileRules.clean(req(null, "pigeon", null, null)));
        assertThrows(IllegalArgumentException.class, () -> ProfileRules.clean(req(null, null, "21A6", null)));
    }

    @Test
    void aValidSaIdFillsTheDateOfBirthAndAMismatchIsRefused() {
        var ok = ProfileRules.demographics(new DemographicsRequest(" Sipho ", "Nkosi", "8001015009087", null, "Male", "male"), TODAY);
        assertEquals(LocalDate.of(1980, 1, 1), ok.dateOfBirth());
        assertEquals("MALE", ok.sexAtBirth());
        assertEquals("Sipho", ok.firstName());
        assertThrows(IllegalArgumentException.class, () -> ProfileRules.demographics(
                new DemographicsRequest("Sipho", "Nkosi", "8001015009087", LocalDate.of(1981, 1, 1), null, null), TODAY));
    }

    @Test
    void namesAreRequiredAndDatesMustBeSensible() {
        assertThrows(IllegalArgumentException.class, () -> ProfileRules.demographics(new DemographicsRequest(" ", "Nkosi", null, null, null, null), TODAY));
        assertThrows(IllegalArgumentException.class, () -> ProfileRules.demographics(new DemographicsRequest("A", "B", null, TODAY.plusDays(1), null, null), TODAY));
        assertThrows(IllegalArgumentException.class, () -> ProfileRules.demographics(new DemographicsRequest("A", "B", null, TODAY.minusYears(131), null, null), TODAY));
        var passport = ProfileRules.demographics(new DemographicsRequest("A", "B", "A1234567", LocalDate.of(1990, 5, 5), null, null), TODAY);
        assertEquals("A1234567", passport.idNumber());
    }

    @Test
    void contactDetailsAreChecked() {
        var c = ProfileRules.contact(new ContactRequest(" 082 123 4567 ", "a@b.co", " Mum ", "0831234567"));
        assertEquals("082 123 4567", c.phone());
        assertEquals("Mum", c.emergencyName());
        assertThrows(IllegalArgumentException.class, () -> ProfileRules.contact(new ContactRequest("123", null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> ProfileRules.contact(new ContactRequest(null, "not-an-email", null, null)));
        assertNull(ProfileRules.contact(new ContactRequest(null, " ", null, null)).email());
    }

    @Test
    void completenessCountsWhatIsThere() {
        var none = ProfileRules.completeness(new ProfileRules.Facts("A", "B", null, null, null, null, null, null, null, null, null, false, false));
        assertEquals(1, none.done());
        assertEquals(9, none.total());
        var all = ProfileRules.completeness(new ProfileRules.Facts("A", "B", LocalDate.of(1990, 1, 1), "FEMALE", "9001015009087", "0821234567",
                "Mum", "0831234567", "1 Road", "Town", "SELF_PAY", false, true));
        assertEquals(100, all.percent());
        var unknownSex = ProfileRules.completeness(new ProfileRules.Facts("A", "B", null, "UNKNOWN", null, null, null, null, null, null, null, true, false));
        assertFalse(unknownSex.items().stream().filter(i -> i.key().equals("sex")).findFirst().orElseThrow().done());
        assertTrue(unknownSex.items().stream().filter(i -> i.key().equals("payer")).findFirst().orElseThrow().done());
    }
}

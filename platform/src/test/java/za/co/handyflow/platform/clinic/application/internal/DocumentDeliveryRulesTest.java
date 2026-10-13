package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentDeliveryRulesTest {

    @Test void onlyAUsableAddressIsUsed() {
        assertEquals("patient@example.org", DocumentDeliveryRules.address("  patient@example.org "));
        assertNull(DocumentDeliveryRules.address("not an address"));
        assertNull(DocumentDeliveryRules.address(" "));
        assertNull(DocumentDeliveryRules.address(null));
    }

    @Test void theEmailNamesTheDocumentAndCarriesNoClinicalText() {
        var m = DocumentDeliveryRules.email("PRESCRIPTION", "RX-2026-000001", "Thandi", "Sample <Practice>");
        assertEquals("Your prescription (RX-2026-000001)", m.subject());
        assertEquals("RX-2026-000001.pdf", m.fileName());
        assertTrue(m.html().contains("Dear Thandi"));
        assertTrue(m.html().contains("Sample &lt;Practice&gt;"), "names are escaped");
        assertTrue(m.html().contains("RX-2026-000001"));
    }

    @Test void aReferralKeepsItsFamiliarWording() {
        var m = DocumentDeliveryRules.email("REFERRAL", "RF-2026-000002", null, null);
        assertEquals("Your referral letter (RF-2026-000002)", m.subject());
        assertTrue(m.html().contains("Dear there"));
        assertTrue(m.html().contains("Please bring it to your appointment."));
    }
}

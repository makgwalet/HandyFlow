package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WhseEvidenceRulesTest {

    @Test @DisplayName("a known type is accepted in any case; anything else is refused with the choices")
    void types() {
        assertEquals("DELIVERY_NOTE", WhseEvidenceRules.requireInboundType(" delivery_note "));
        assertEquals("DAMAGE_PHOTO", WhseEvidenceRules.requireInboundType("DAMAGE_PHOTO"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> WhseEvidenceRules.requireInboundType("SIGNED_POD"));
        assertTrue(e.getMessage().contains("DELIVERY_NOTE"));
        assertThrows(IllegalArgumentException.class, () -> WhseEvidenceRules.requireInboundType(null));
        assertThrows(IllegalArgumentException.class, () -> WhseEvidenceRules.requireInboundType(""));
    }

    @Test @DisplayName("photos, PDFs, text and Office files are accepted; names with a path or an odd type are not")
    void files() {
        assertTrue(WhseEvidenceRules.acceptableFile("note.pdf", "application/pdf"));
        assertTrue(WhseEvidenceRules.acceptableFile("dent.jpg", "image/jpeg"));
        assertTrue(WhseEvidenceRules.acceptableFile("list.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertTrue(WhseEvidenceRules.acceptableFile("list.csv", "text/csv"));
        assertFalse(WhseEvidenceRules.acceptableFile("x.exe", "application/x-msdownload"));
        assertFalse(WhseEvidenceRules.acceptableFile("../x.pdf", "application/pdf"));
        assertFalse(WhseEvidenceRules.acceptableFile("a/b.pdf", "application/pdf"));
        assertFalse(WhseEvidenceRules.acceptableFile(" ", "application/pdf"));
        assertFalse(WhseEvidenceRules.acceptableFile("a.pdf", null));
    }
}

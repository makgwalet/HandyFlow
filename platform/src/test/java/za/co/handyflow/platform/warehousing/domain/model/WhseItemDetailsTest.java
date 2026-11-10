package za.co.handyflow.platform.warehousing.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class WhseItemDetailsTest {

    private static BigDecimal n(String v) { return new BigDecimal(v); }

    private static WhseItemDetails with(String barcode, String min, String max, String temp) {
        return new WhseItemDetails(barcode, null, null, null, null, null, min == null ? null : n(min), max == null ? null : n(max), temp, false, false, true);
    }

    @Test @DisplayName("GTIN check digits: valid EAN-13, UPC-A, EAN-8 and GTIN-14 pass; a wrong digit, letters and odd lengths fail")
    void gtin() {
        assertTrue(Gtin.isValid("4006381333931"));   // EAN-13
        assertTrue(Gtin.isValid("036000291452"));    // UPC-A
        assertTrue(Gtin.isValid("96385074"));        // EAN-8
        assertTrue(Gtin.isValid("10012345678902"));  // GTIN-14
        assertFalse(Gtin.isValid("4006381333932"));
        assertFalse(Gtin.isValid("40063813339A1"));
        assertFalse(Gtin.isValid("123456789"));
        assertFalse(Gtin.isValid(null));
    }

    @Test @DisplayName("a blank barcode means none; an invalid one is refused with a clear message")
    void normalise() {
        assertNull(Gtin.normalise("  "));
        assertEquals("4006381333931", Gtin.normalise(" 4006381333931 "));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Gtin.normalise("4006381333932")).getMessage().contains("not a valid barcode"));
    }

    @Test @DisplayName("minimum cannot exceed maximum; negatives and zero sizes are refused")
    void levels() {
        assertThrows(IllegalArgumentException.class, () -> with(null, "10", "5", null));
        assertThrows(IllegalArgumentException.class, () -> with(null, "-1", null, null));
        assertDoesNotThrow(() -> with(null, "5", "5", null));
        assertThrows(IllegalArgumentException.class, () -> new WhseItemDetails(null, n("0"), null, null, null, null, null, null, null, false, false, true));
        assertThrows(IllegalArgumentException.class, () -> new WhseItemDetails(null, null, null, null, n("-2"), null, null, null, null, false, false, true));
        assertThrows(IllegalArgumentException.class, () -> new WhseItemDetails(null, null, null, null, null, n("-0.01"), null, null, null, false, false, true));
    }

    @Test @DisplayName("temperature class defaults to AMBIENT, is case-insensitive, and rejects unknown values")
    void temperature() {
        assertEquals("AMBIENT", with(null, null, null, null).temperatureClass());
        assertEquals("FROZEN", with(null, null, null, " frozen ").temperatureClass());
        assertThrows(IllegalArgumentException.class, () -> with(null, null, null, "WARM"));
    }

    @Test @DisplayName("volume is computed only when all three dimensions are known")
    void volume() {
        var full = new WhseItemDetails(null, n("1000"), n("500"), n("400"), null, null, null, null, null, false, false, true);
        assertEquals(0, n("0.2").compareTo(full.volumeM3()));
        assertNull(new WhseItemDetails(null, n("1000"), n("500"), null, null, null, null, null, null, false, false, true).volumeM3());
    }
}

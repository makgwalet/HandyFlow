package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.clinic.application.internal.LabMarkerRules.Input;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LabMarkerRulesTest {

    private static BigDecimal d(String s) { return s == null ? null : new BigDecimal(s); }

    private static Input in(String marker, String value, String refLow, String refHigh, String critLow, String critHigh, String flag) {
        return new Input(marker, value, "mmol/L", d(refLow), d(refHigh), d(critLow), d(critHigh), flag);
    }

    private static String flagOf(Input i) {
        return LabMarkerRules.evaluate(List.of(i)).markers().get(0).flag();
    }

    @Test
    void judgesANumberAgainstTheLabsOwnRange() {
        assertEquals("NORMAL", flagOf(in("K", "4.2", "3.5", "5.1", null, null, null)));
        assertEquals("NORMAL", flagOf(in("K", "3.5", "3.5", "5.1", null, null, null)));   // on the limit is normal
        assertEquals("LOW", flagOf(in("K", "3.4", "3.5", "5.1", null, null, null)));
        assertEquals("HIGH", flagOf(in("K", "5.2", "3.5", "5.1", null, null, null)));
        assertEquals("LOW", flagOf(in("K", "3.0", "3.5", null, null, null, null)));        // only a lower limit
        assertEquals("NORMAL", flagOf(in("K", "9", "3.5", null, null, null, null)));
        assertEquals("HIGH", flagOf(in("CRP", "12", null, "10", null, null, null)));       // only an upper limit
    }

    @Test
    void criticalIsAtOrBeyondTheLabsCriticalLimit() {
        assertEquals("CRITICAL", flagOf(in("K", "2.4", "3.5", "5.1", "2.5", "6.5", null)));
        assertEquals("CRITICAL", flagOf(in("K", "2.5", "3.5", "5.1", "2.5", "6.5", null)));   // exactly on the limit
        assertEquals("CRITICAL", flagOf(in("K", "6.5", "3.5", "5.1", "2.5", "6.5", null)));
        assertEquals("LOW", flagOf(in("K", "2.6", "3.5", "5.1", "2.5", "6.5", null)));
        assertEquals("HIGH", flagOf(in("K", "6.4", "3.5", "5.1", "2.5", "6.5", null)));
        assertEquals("CRITICAL", flagOf(in("K", "9", null, null, null, "6.5", null)));         // critical limit alone is enough
    }

    @Test
    void aNumberWithNoRangeIsUnknownNotNormal() {
        assertEquals("UNKNOWN", flagOf(in("Glucose", "7.2", null, null, null, null, null)));
    }

    @Test
    void aTextResultIsUnknownUntilTheClinicianFlagsIt() {
        assertEquals("UNKNOWN", flagOf(in("HIV", "Positive", null, null, null, null, null)));
        assertEquals("ABNORMAL", flagOf(in("HIV", "Positive", null, null, null, null, "abnormal")));
        assertEquals("NORMAL", flagOf(in("HIV", "Negative", null, null, null, null, "NORMAL")));
        assertEquals("UNKNOWN", flagOf(in("TSH", "<0.01", null, null, null, null, null)));
    }

    @Test
    void theClinicianCanEscalateButNeverDowngrade() {
        assertEquals("CRITICAL", flagOf(in("K", "4.2", "3.5", "5.1", null, null, "CRITICAL")));
        assertEquals("HIGH", flagOf(in("K", "4.2", "3.5", "5.1", null, null, "HIGH")));
        assertEquals("CRITICAL", flagOf(in("K", "2.0", "3.5", "5.1", "2.5", "6.5", "NORMAL")));  // computed critical wins
        assertEquals("LOW", flagOf(in("K", "3.0", "3.5", "5.1", null, null, "NORMAL")));         // computed low wins
    }

    @Test
    void acceptsADecimalComma() {
        assertEquals("HIGH", flagOf(in("K", "5,2", "3.5", "5.1", null, null, null)));
        assertNull(LabMarkerRules.parse("abc"));
        assertNull(LabMarkerRules.parse("  "));
        assertEquals(new BigDecimal("12"), LabMarkerRules.parse(" 12 "));
    }

    @Test
    void rollsUpAbnormalAndCritical() {
        var normal = LabMarkerRules.evaluate(List.of(in("K", "4.2", "3.5", "5.1", null, null, null)));
        assertFalse(normal.anyAbnormal());
        assertFalse(normal.anyCritical());
        var unknownOnly = LabMarkerRules.evaluate(List.of(in("G", "7.2", null, null, null, null, null)));
        assertFalse(unknownOnly.anyAbnormal());   // unknown is neither
        var high = LabMarkerRules.evaluate(List.of(in("K", "4.2", "3.5", "5.1", null, null, null), in("Na", "150", "135", "145", null, null, null)));
        assertTrue(high.anyAbnormal());
        assertFalse(high.anyCritical());
        var crit = LabMarkerRules.evaluate(List.of(in("K", "7", "3.5", "5.1", "2.5", "6.5", null)));
        assertTrue(crit.anyAbnormal());
        assertTrue(crit.anyCritical());
        var empty = LabMarkerRules.evaluate(List.of());
        assertFalse(empty.anyAbnormal());
        assertEquals("[]", empty.json());
    }

    @Test
    void refusesInputsThatMakeNoSense() {
        assertThrows(IllegalArgumentException.class, () -> flagOf(in(" ", "4", null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> flagOf(in("K", " ", null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> flagOf(in("K", "4", "5", "3", null, null, null)));        // low above high
        assertThrows(IllegalArgumentException.class, () -> flagOf(in("K", "4", "3.5", "5.1", "4", null, null)));     // crit low above ref low
        assertThrows(IllegalArgumentException.class, () -> flagOf(in("K", "4", "3.5", "5.1", null, "5", null)));     // crit high below ref high
        assertThrows(IllegalArgumentException.class, () -> flagOf(in("K", "4", null, null, "6", "6", null)));        // critical limits overlap
        assertThrows(IllegalArgumentException.class, () -> flagOf(in("K", "4", null, null, null, null, "SEVERE")));
        assertThrows(IllegalArgumentException.class, () -> flagOf(in("x".repeat(101), "4", null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> flagOf(in("K", "9".repeat(51), null, null, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> LabMarkerRules.evaluate(java.util.Collections.nCopies(101, in("K", "4", null, null, null, null, null))));
    }

    @Test
    void refRangeReadsNaturally() {
        assertEquals("3.5–5.1", LabMarkerRules.refRange(d("3.50"), d("5.10")));
        assertEquals("≥ 3.5", LabMarkerRules.refRange(d("3.5"), null));
        assertEquals("≤ 10", LabMarkerRules.refRange(null, d("10.0")));
        assertNull(LabMarkerRules.refRange(null, null));
    }

    @Test
    void jsonEscapesWhatNeedsEscaping() {
        var e = LabMarkerRules.evaluate(List.of(new Input("Test \"A\"\\B\u0001C", "Pos\nitive", null, null, null, null, null, "ABNORMAL")));
        String j = e.json();
        assertTrue(j.contains("\"marker\":\"Test \\\"A\\\"\\\\B\\u0001C\""), j);
        assertTrue(j.contains("\"value\":\"Pos itive\""), j);   // line breaks are tidied to a space
        assertTrue(j.contains("\"unit\":null"), j);
        assertTrue(j.contains("\"flag\":\"ABNORMAL\""), j);
    }
}

package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.warehousing.application.internal.WhsePackingRules.LineState;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WhsePackingRulesTest {

    private static BigDecimal bd(String v) { return new BigDecimal(v); }
    private static LineState line(String toShip, String packed) { return new LineState("SKU-1", bd(toShip), bd(packed)); }

    @Test @DisplayName("package type defaults to a box and is checked")
    void types() {
        assertEquals("BOX", WhsePackingRules.type(null));
        assertEquals("PALLET", WhsePackingRules.type(" pallet "));
        assertThrows(IllegalArgumentException.class, () -> WhsePackingRules.type("bag"));
    }

    @Test @DisplayName("dimensions are optional but must be sensible")
    void dimensions() {
        assertNull(WhsePackingRules.dimension(null, "Length"));
        assertEquals(300, WhsePackingRules.dimension(300, "Length"));
        assertThrows(IllegalArgumentException.class, () -> WhsePackingRules.dimension(0, "Length"));
        assertThrows(IllegalArgumentException.class, () -> WhsePackingRules.dimension(20_001, "Width"));
    }

    @Test @DisplayName("a quantity cannot exceed what is left to pack")
    void addLimits() {
        assertDoesNotThrow(() -> WhsePackingRules.checkAdd(line("10", "4"), BigDecimal.ZERO, bd("6")));
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> WhsePackingRules.checkAdd(line("10", "4"), BigDecimal.ZERO, bd("7")));
        assertTrue(e.getMessage().contains("only 6"));
        assertThrows(IllegalArgumentException.class, () -> WhsePackingRules.checkAdd(line("10", "0"), BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> WhsePackingRules.checkAdd(line("10", "0"), BigDecimal.ZERO, bd("1.2345")));
    }

    @Test @DisplayName("changing a line's quantity in a package does not count it twice")
    void replaceNotDouble() {
        // 10 ship, 8 packed of which 5 are in this package: this package can go up to 7.
        assertDoesNotThrow(() -> WhsePackingRules.checkAdd(line("10", "8"), bd("5"), bd("7")));
        assertThrows(IllegalStateException.class, () -> WhsePackingRules.checkAdd(line("10", "8"), bd("5"), bd("8")));
    }

    @Test @DisplayName("closing needs contents and a gross weight above the tare")
    void close() {
        assertThrows(IllegalStateException.class, () -> WhsePackingRules.checkClose(0, bd("5"), bd("1")));
        assertThrows(IllegalArgumentException.class, () -> WhsePackingRules.checkClose(1, null, bd("1")));
        assertThrows(IllegalArgumentException.class, () -> WhsePackingRules.checkClose(1, bd("1"), bd("1")));
        assertThrows(IllegalArgumentException.class, () -> WhsePackingRules.checkClose(1, bd("0"), BigDecimal.ZERO));
        assertDoesNotThrow(() -> WhsePackingRules.checkClose(2, bd("5.5"), bd("0.5")));
    }

    @Test @DisplayName("a weight far from the item master is flagged, close to it is not")
    void weightNote() {
        assertNull(WhsePackingRules.weightNote(bd("10.5"), bd("0.5"), bd("10")));
        assertTrue(WhsePackingRules.weightNote(bd("5.5"), bd("0.5"), bd("10")).startsWith("Lighter"));
        assertTrue(WhsePackingRules.weightNote(bd("20.5"), bd("0.5"), bd("10")).startsWith("Heavier"));
        assertNull(WhsePackingRules.weightNote(bd("5"), bd("0"), null));
        assertNull(WhsePackingRules.weightNote(null, bd("0"), bd("5")));
    }

    @Test @DisplayName("an order with no packages is never blocked")
    void optional() {
        assertTrue(WhsePackingRules.notReady(0, 0, List.of(line("10", "0"))).isEmpty());
    }

    @Test @DisplayName("open packages and unpacked units block marking packed")
    void blockers() {
        List<String> p = WhsePackingRules.notReady(2, 1, List.of(line("10", "6")));
        assertEquals(2, p.size());
        assertTrue(p.get(0).contains("still open"));
        assertTrue(p.get(1).contains("4 not packed"));
        assertTrue(WhsePackingRules.notReady(2, 0, List.of(line("10", "10"))).isEmpty());
    }

    @Test @DisplayName("more packed than ships is reported (a line short-picked after packing)")
    void overPacked() {
        assertTrue(WhsePackingRules.notReady(1, 0, List.of(line("8", "10"))).get(0).contains("more is packed"));
    }
}

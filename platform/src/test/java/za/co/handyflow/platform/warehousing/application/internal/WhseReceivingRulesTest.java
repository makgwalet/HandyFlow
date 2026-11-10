package za.co.handyflow.platform.warehousing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WhseReceivingRulesTest {

    private static BigDecimal n(String v) { return new BigDecimal(v); }

    private static List<String> c(String exp, String rec, String dmg, String held, boolean unexpected, boolean fin) {
        return WhseReceivingRules.classify(n(exp), n(rec), n(dmg), n(held), unexpected, fin);
    }

    @Test @DisplayName("a partial delivery is outstanding, not short, until the delivery is finalised")
    void shortOnlyWhenFinal() {
        assertTrue(c("10", "6", "0", "0", false, false).isEmpty());
        assertEquals(List.of("SHORT"), c("10", "6", "0", "0", false, true));
    }

    @Test @DisplayName("more than expected is over; exactly expected is clean")
    void over() {
        assertEquals(List.of("OVER"), c("10", "12", "0", "0", false, true));
        assertTrue(c("10", "10", "0", "0", false, true).isEmpty());
    }

    @Test @DisplayName("an item not on the advice is flagged only once something was received, and is never also over")
    void unexpected() {
        assertTrue(c("0", "0", "0", "0", true, false).isEmpty());
        assertEquals(List.of("UNEXPECTED_ITEM"), c("0", "3", "0", "0", true, true));
    }

    @Test @DisplayName("damaged and held stock are reported alongside shortfalls")
    void damagedAndHeld() {
        assertEquals(List.of("SHORT", "DAMAGED", "HELD_FOR_INSPECTION"), c("10", "8", "2", "3", false, true));
    }

    @Test @DisplayName("held stock alone does not need a sign-off note; anything else does")
    void signOff() {
        assertFalse(WhseReceivingRules.needsSignOff(List.of("HELD_FOR_INSPECTION")));
        assertFalse(WhseReceivingRules.needsSignOff(List.of()));
        assertTrue(WhseReceivingRules.needsSignOff(List.of("HELD_FOR_INSPECTION", "DAMAGED")));
        assertTrue(WhseReceivingRules.needsSignOff(List.of("SHORT")));
    }

    @Test @DisplayName("short and over quantities never go negative")
    void quantities() {
        assertEquals(0, n("4").compareTo(WhseReceivingRules.shortQty(n("10"), n("6"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(WhseReceivingRules.shortQty(n("10"), n("12"))));
        assertEquals(0, n("2").compareTo(WhseReceivingRules.overQty(n("10"), n("12"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(WhseReceivingRules.overQty(n("10"), n("6"))));
    }
}

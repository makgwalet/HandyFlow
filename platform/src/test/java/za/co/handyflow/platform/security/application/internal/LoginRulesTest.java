package za.co.handyflow.platform.security.application.internal;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginRulesTest {
    @Test void none() { assertEquals(LoginRules.PickKind.NONE, LoginRules.pickByPhone(List.<String>of(), g -> true).kind()); }
    @Test void singleCandidateIsReturnedWithoutPinCheck() {
        var p = LoginRules.pickByPhone(List.of("a"), g -> false);
        assertEquals(LoginRules.PickKind.ONE, p.kind()); assertEquals("a", p.guard());
    }
    @Test void pinPicksTheAccount() {
        var p = LoginRules.pickByPhone(List.of("a", "b"), g -> g.equals("b"));
        assertEquals(LoginRules.PickKind.ONE, p.kind()); assertEquals("b", p.guard());
    }
    @Test void pinFitsNone() { assertEquals(LoginRules.PickKind.NONE, LoginRules.pickByPhone(List.of("a", "b"), g -> false).kind()); }
    @Test void pinFitsBoth() { assertEquals(LoginRules.PickKind.AMBIGUOUS, LoginRules.pickByPhone(List.of("a", "b"), g -> true).kind()); }
    @Test void lock() {
        assertFalse(LoginRules.lockBlocks(false, "d1", "x"));
        assertTrue(LoginRules.lockBlocks(true, "d1", "x"));
        assertTrue(LoginRules.lockBlocks(true, null, "d1"));
        assertTrue(LoginRules.lockBlocks(true, "d1", null));
        assertFalse(LoginRules.lockBlocks(true, "d1", "d1"));
    }
}

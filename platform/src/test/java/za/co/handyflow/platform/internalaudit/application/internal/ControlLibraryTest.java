package za.co.handyflow.platform.internalaudit.application.internal;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ControlLibraryTest {
    @Test
    void everyControlIsCompleteAndCodesAreUnique() {
        var codes = new HashSet<String>();
        for (var c : ControlLibrary.ALL) {
            assertTrue(codes.add(c.code()), "duplicate " + c.code());
            assertFalse(c.objective().isBlank());
            assertTrue(c.procedure().length() > 30, c.code() + " procedure too thin");
            assertFalse(c.area().isBlank());
            assertTrue(ControlLibrary.DOMAINS.contains(c.domain()), c.code());
            assertNotNull(c.reference());
        }
        assertEquals(50, ControlLibrary.ALL.size());
    }

    @Test
    void codesCarryTheirDomainPrefix() {
        var prefix = java.util.Map.of("IT", "IT-", "CYBER", "CY-", "OPERATIONAL", "OP-", "COMPLIANCE", "CO-");
        for (var c : ControlLibrary.ALL) assertTrue(c.code().startsWith(prefix.get(c.domain())), c.code());
    }

    @Test
    void everyComplianceControlThatNamesALawGivesAReference() {
        for (var c : ControlLibrary.forDomain("COMPLIANCE")) if (!c.code().equals("CO-14")) assertFalse(c.reference().isBlank(), c.code());
    }

    @Test
    void selectCombinesDomainsAndCodesWithoutDuplicates() {
        var l = ControlLibrary.select(List.of("cyber"), List.of("it-01", "CY-01"));
        assertEquals(11, l.size());
        assertEquals("IT-01", l.get(l.size() - 1).code());
    }

    @Test
    void unknownDomainOrCodeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ControlLibrary.select(List.of("HR"), null));
        assertThrows(IllegalArgumentException.class, () -> ControlLibrary.select(null, List.of("ZZ-99")));
        assertTrue(ControlLibrary.find("nope").isEmpty());
        assertTrue(ControlLibrary.find(null).isEmpty());
    }
}

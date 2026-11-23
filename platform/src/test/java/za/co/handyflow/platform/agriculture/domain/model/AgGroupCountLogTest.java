package za.co.handyflow.platform.agriculture.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AgGroupCountLogTest {

    private static AgGroupCountLog log(int before, int after, String reason) {
        return AgGroupCountLog.create(TenantId.generate(), UUID.randomUUID(), "DECREASE", before, after, reason, null, null, null, "Thandi");
    }

    @Test void deltaIsAfterMinusBefore() {
        assertEquals(-20, log(50, 30, "Sold 20").getDelta());
        assertEquals(5, log(30, 35, "Calves counted").getDelta());
    }

    @Test void keepsTheReasonTrimmedAndWhoDidIt() {
        AgGroupCountLog l = log(50, 30, "  Sold 20  ");
        assertEquals("Sold 20", l.getReason());
        assertEquals("Thandi", l.getCreatedByName());
        assertEquals(50, l.getCountBefore());
        assertEquals(30, l.getCountAfter());
    }

    @Test void refusesNoReasonAndNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () -> log(50, 30, " "));
        assertThrows(IllegalArgumentException.class, () -> log(50, 30, null));
        assertThrows(IllegalArgumentException.class, () -> log(50, -1, "x y z"));
        assertThrows(IllegalArgumentException.class, () -> log(-1, 5, "x y z"));
    }
}

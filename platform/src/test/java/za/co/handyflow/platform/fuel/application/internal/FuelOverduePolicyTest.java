package za.co.handyflow.platform.fuel.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static za.co.handyflow.platform.fuel.application.internal.FuelOverduePolicy.Status.*;

class FuelOverduePolicyTest {

    static final Instant SCHEDULED = Instant.parse("2026-11-17T08:00:00Z");
    static Instant after(int minutes) { return SCHEDULED.plusSeconds(minutes * 60L); }

    @Test @DisplayName("on schedule until the scheduled time, delayed inside the 120-minute grace, overdue after it, escalated after the escalation period")
    void bands() {
        assertEquals(ON_SCHEDULE, FuelOverduePolicy.status(SCHEDULED, after(-5), 120, 60));
        assertEquals(ON_SCHEDULE, FuelOverduePolicy.status(SCHEDULED, SCHEDULED, 120, 60));
        assertEquals(DELAYED, FuelOverduePolicy.status(SCHEDULED, after(1), 120, 60));
        assertEquals(DELAYED, FuelOverduePolicy.status(SCHEDULED, after(119), 120, 60));
        assertEquals(OVERDUE, FuelOverduePolicy.status(SCHEDULED, after(120), 120, 60));
        assertEquals(OVERDUE, FuelOverduePolicy.status(SCHEDULED, after(179), 120, 60));
        assertEquals(ESCALATED, FuelOverduePolicy.status(SCHEDULED, after(180), 120, 60));
    }

    @Test @DisplayName("isOverdue agrees with the status bands, and a company's own grace is honoured")
    void agrees() {
        assertFalse(FuelOverduePolicy.isOverdue(SCHEDULED, after(119), 120));
        assertTrue(FuelOverduePolicy.isOverdue(SCHEDULED, after(120), 120));
        assertTrue(FuelOverduePolicy.isOverdue(SCHEDULED, after(30), 30));
        assertFalse(FuelOverduePolicy.isOverdue(null, after(500), 120));
    }

    @Test @DisplayName("a reschedule moves the scheduled time, so the clock restarts with it")
    void reschedule() {
        Instant moved = after(300);
        assertTrue(FuelOverduePolicy.isOverdue(SCHEDULED, after(310), 120));
        assertFalse(FuelOverduePolicy.isOverdue(moved, after(310), 120));
    }
}

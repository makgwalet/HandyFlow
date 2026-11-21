package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SapsChaseTest {
    private static final Instant DUE = Instant.parse("2026-11-21T10:00:00Z");

    @Test void notYetOverdueNoAlert() { assertFalse(SapsChase.shouldAlert(DUE.minusSeconds(1), DUE, null, null)); }
    @Test void firstAlertWhenOverdue() { assertTrue(SapsChase.shouldAlert(DUE.plusSeconds(1), DUE, null, null)); }
    @Test void caseNumberSilencesIt() { assertFalse(SapsChase.shouldAlert(DUE.plusSeconds(9999), DUE, "CAS 1", null)); }
    @Test void noRepeatWithin24Hours() { assertFalse(SapsChase.shouldAlert(DUE.plusSeconds(7200), DUE, null, DUE.plusSeconds(60))); }
    @Test void repeatsAfter24Hours() {
        Instant last = DUE.plusSeconds(60);
        assertTrue(SapsChase.shouldAlert(last.plusSeconds(86400), DUE, null, last));
        assertFalse(SapsChase.shouldAlert(last.plusSeconds(86399), DUE, null, last));
    }
}

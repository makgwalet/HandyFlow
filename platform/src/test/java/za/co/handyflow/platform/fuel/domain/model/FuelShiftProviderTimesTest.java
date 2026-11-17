package za.co.handyflow.platform.fuel.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Written, not compiled or run. */
class FuelShiftProviderTimesTest {

    private FuelShift shift() { return FuelShift.open(TenantId.of(UUID.randomUUID()), "SH-1", "Thabo", null, null); }

    @Test @DisplayName("provider times are kept beside the system opened time, which is not changed")
    void keepsSystemTime() {
        FuelShift s = shift();
        Instant system = s.getOpenedAt();
        s.recordProviderTimes(Instant.parse("2026-11-12T06:00:00Z"), Instant.parse("2026-11-12T14:00:00Z"));
        assertEquals(Instant.parse("2026-11-12T06:00:00Z"), s.getProviderOpenedAt());
        assertEquals(Instant.parse("2026-11-12T14:00:00Z"), s.getProviderClosedAt());
        assertEquals(system, s.getOpenedAt());
    }

    @Test @DisplayName("a closing time before the opening time is refused; one time alone is accepted")
    void validates() {
        FuelShift s = shift();
        assertThrows(IllegalArgumentException.class, () -> s.recordProviderTimes(Instant.parse("2026-11-12T14:00:00Z"), Instant.parse("2026-11-12T06:00:00Z")));
        s.recordProviderTimes(null, Instant.parse("2026-11-12T14:00:00Z"));
        assertNull(s.getProviderOpenedAt());
        assertNotNull(s.getProviderClosedAt());
    }
}

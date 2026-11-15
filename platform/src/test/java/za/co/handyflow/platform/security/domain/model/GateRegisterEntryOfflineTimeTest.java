package za.co.handyflow.platform.security.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A visitor logged while the phone had no signal keeps the times the guard recorded, never nonsense ones. */
class GateRegisterEntryOfflineTimeTest {

    private GateRegisterEntry arrived() {
        return GateRegisterEntry.logArrival(TenantId.generate(), UUID.randomUUID(), UUID.randomUUID(), "VISITOR", "Thabo",
                null, null, null, null, null, null, null, null, null, null, UUID.randomUUID());
    }

    @Test
    void anArrivalRecordedEarlierOnThePhoneMovesBackInTime() {
        GateRegisterEntry e = arrived();
        Instant earlier = Instant.now().minus(30, ChronoUnit.MINUTES);
        e.recordedOnPhoneAt(earlier);
        assertThat(e.getLoggedInAt()).isEqualTo(earlier);
    }

    @Test
    void aPhoneTimeAfterTheServerTimeNeverMovesAnArrivalForward() {
        GateRegisterEntry e = arrived();
        Instant before = e.getLoggedInAt();
        e.recordedOnPhoneAt(before.plus(1, ChronoUnit.HOURS));
        assertThat(e.getLoggedInAt()).isEqualTo(before);
    }

    @Test
    void aDepartureKeepsThePhonesTime() {
        GateRegisterEntry e = arrived();
        e.recordedOnPhoneAt(Instant.now().minus(60, ChronoUnit.MINUTES));
        Instant left = Instant.now().minus(10, ChronoUnit.MINUTES);
        e.logExit(UUID.randomUUID(), left);
        assertThat(e.getLoggedOutAt()).isEqualTo(left);
        assertThat(e.getStatus()).isEqualTo("DEPARTED");
    }

    @Test
    void aDepartureRecordedBeforeTheArrivalIsHeldAtTheArrival() {
        GateRegisterEntry e = arrived();
        e.logExit(UUID.randomUUID(), e.getLoggedInAt().minus(5, ChronoUnit.MINUTES));
        assertThat(e.getLoggedOutAt()).isEqualTo(e.getLoggedInAt());
    }
}

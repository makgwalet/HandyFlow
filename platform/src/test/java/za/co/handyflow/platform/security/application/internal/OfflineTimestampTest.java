package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Which time a late-synced record is stamped with. */
class OfflineTimestampTest {

    private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

    @Test
    void noTimeFromThePhoneMeansNow() {
        assertThat(OfflineTimestamp.accept(null, NOW)).isEqualTo(NOW);
    }

    @Test
    void aSensibleEarlierTimeIsKept() {
        Instant earlier = NOW.minus(Duration.ofHours(3));
        assertThat(OfflineTimestamp.accept(earlier, NOW)).isEqualTo(earlier);
    }

    @Test
    void smallClockDriftIntoTheFutureIsKept() {
        Instant slightlyAhead = NOW.plus(Duration.ofMinutes(4));
        assertThat(OfflineTimestamp.accept(slightlyAhead, NOW)).isEqualTo(slightlyAhead);
    }

    @Test
    void aTimeWellIntoTheFutureIsReplacedByNow() {
        assertThat(OfflineTimestamp.accept(NOW.plus(Duration.ofHours(2)), NOW)).isEqualTo(NOW);
    }

    @Test
    void aTimeOlderThan48HoursIsRefused() {
        assertThatThrownBy(() -> OfflineTimestamp.accept(NOW.minus(Duration.ofHours(49)), NOW))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("48 hours");
    }

    @Test
    void exactly48HoursIsStillAccepted() {
        Instant edge = NOW.minus(Duration.ofHours(48));
        assertThat(OfflineTimestamp.accept(edge, NOW)).isEqualTo(edge);
    }

    @Test
    void aLateRecordGetsANoteAndAFreshOneDoesNot() {
        assertThat(OfflineTimestamp.lateNote(NOW.minus(Duration.ofMinutes(30)), NOW))
                .contains("Raised on the guard's phone at 2026-11-12 09:30 UTC").contains("received 2026-11-12 10:00 UTC");
        assertThat(OfflineTimestamp.lateNote(NOW.minusSeconds(30), NOW)).isEmpty();
        assertThat(OfflineTimestamp.lateNote(null, NOW)).isEmpty();
        assertThat(OfflineTimestamp.lateNote(NOW, NOW)).isEmpty();
    }
}

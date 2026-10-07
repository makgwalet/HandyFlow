package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Lateness from the real start of a shift. */
class ShiftPunctualityTest {

    private static final Instant START = Instant.parse("2026-10-07T04:00:00Z");

    @Test @DisplayName("Minutes late are whole minutes after the scheduled start; early is zero; no recorded start is unknown")
    void minutes() {
        assertThat(ShiftPunctuality.minutesLate(START, START.plusSeconds(22 * 60 + 59))).isEqualTo(22);
        assertThat(ShiftPunctuality.minutesLate(START, START.minusSeconds(300))).isZero();
        assertThat(ShiftPunctuality.minutesLate(START, null)).isNull();
    }

    @Test @DisplayName("Late means more than 15 minutes after the scheduled start")
    void graceBoundary() {
        assertThat(ShiftPunctuality.late(START, START.plusSeconds(15 * 60), null)).isFalse();
        assertThat(ShiftPunctuality.late(START, START.plusSeconds(16 * 60), null)).isTrue();
        assertThat(ShiftPunctuality.late(START, START.minusSeconds(600), null)).isFalse();
    }

    @Test @DisplayName("With a recorded start the alert is ignored; without one the alert decides")
    void alertFallback() {
        assertThat(ShiftPunctuality.late(START, START.plusSeconds(5 * 60), Instant.now())).isFalse();
        assertThat(ShiftPunctuality.late(START, null, Instant.now())).isTrue();
        assertThat(ShiftPunctuality.late(START, null, null)).isFalse();
    }
}

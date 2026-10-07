package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** GPS liveness for a guard on shift: live, stale, or no position since the shift began. */
class LiveStatusTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final Instant START = NOW.minus(Duration.ofHours(3));

    @Test @DisplayName("A ping within five minutes is live")
    void live() {
        assertThat(LiveStatus.gps(NOW.minus(Duration.ofMinutes(2)), START, NOW)).isEqualTo("LIVE");
        assertThat(LiveStatus.gps(NOW.minus(Duration.ofMinutes(5)), START, NOW)).isEqualTo("LIVE");
    }

    @Test @DisplayName("An older ping this shift is stale")
    void stale() { assertThat(LiveStatus.gps(NOW.minus(Duration.ofMinutes(6)), START, NOW)).isEqualTo("STALE"); }

    @Test @DisplayName("No ping, or only a ping from before the shift began, is no GPS")
    void noGps() {
        assertThat(LiveStatus.gps(null, START, NOW)).isEqualTo("NO_GPS");
        assertThat(LiveStatus.gps(START.minus(Duration.ofMinutes(1)), START, NOW)).isEqualTo("NO_GPS");
        assertThat(LiveStatus.gps(NOW.minus(Duration.ofMinutes(1)), null, NOW)).isEqualTo("LIVE");
    }

    @Test @DisplayName("A shift past its planned end is overrunning")
    void overrunning() {
        assertThat(LiveStatus.overrunning(NOW.minus(Duration.ofMinutes(1)), NOW)).isTrue();
        assertThat(LiveStatus.overrunning(NOW.plus(Duration.ofMinutes(1)), NOW)).isFalse();
        assertThat(LiveStatus.overrunning(null, NOW)).isFalse();
    }
}

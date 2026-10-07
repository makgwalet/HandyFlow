package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.dto.LiveGuardResponse;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Row mapping for Live Operations: positions are shown only when they were taken during the current shift. */
class LiveOperationsServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final Instant START = NOW.minus(Duration.ofHours(3));

    private static ResultSet row(Instant recorded, Instant scan, Instant end) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("guard_id")).thenReturn(UUID.randomUUID());
        when(rs.getObject("shift_id")).thenReturn(UUID.randomUUID());
        when(rs.getObject("site_id")).thenReturn(UUID.randomUUID());
        when(rs.getString("first_name")).thenReturn("Thabo");
        when(rs.getString("last_name")).thenReturn("Mokoena");
        when(rs.getString("grade")).thenReturn("C");
        when(rs.getString("site_name")).thenReturn("ABC Mall");
        when(rs.getTimestamp("start_at")).thenReturn(Timestamp.from(START));
        when(rs.getTimestamp("end_at")).thenReturn(Timestamp.from(end));
        when(rs.getTimestamp("recorded_at")).thenReturn(recorded == null ? null : Timestamp.from(recorded));
        when(rs.getBigDecimal("latitude")).thenReturn(new BigDecimal("-26.1076"));
        when(rs.getBigDecimal("longitude")).thenReturn(new BigDecimal("28.0567"));
        when(rs.getTimestamp("last_scan_at")).thenReturn(scan == null ? null : Timestamp.from(scan));
        when(rs.getString("checkpoint_name")).thenReturn(scan == null ? null : "North gate");
        return rs;
    }

    @Test @DisplayName("A recent ping gives a live position and the last scan")
    void live() throws Exception {
        LiveGuardResponse r = LiveOperationsService.map(row(NOW.minus(Duration.ofMinutes(1)), NOW.minus(Duration.ofMinutes(20)), NOW.plus(Duration.ofHours(5))), NOW);
        assertThat(r.gpsState()).isEqualTo("LIVE");
        assertThat(r.latitude()).isEqualByComparingTo("-26.1076");
        assertThat(r.guardName()).isEqualTo("Thabo Mokoena");
        assertThat(r.lastScanCheckpoint()).isEqualTo("North gate");
        assertThat(r.overrunning()).isFalse();
    }

    @Test @DisplayName("A ping from before the shift began is not shown as a position")
    void oldPingHidden() throws Exception {
        LiveGuardResponse r = LiveOperationsService.map(row(START.minus(Duration.ofHours(10)), null, NOW.plus(Duration.ofHours(5))), NOW);
        assertThat(r.gpsState()).isEqualTo("NO_GPS");
        assertThat(r.latitude()).isNull();
        assertThat(r.longitude()).isNull();
        assertThat(r.recordedAt()).isNull();
        assertThat(r.lastScanAt()).isNull();
    }

    @Test @DisplayName("A guard who never pinged is listed with no position, and an old ping this shift is stale but kept")
    void noPingAndStale() throws Exception {
        assertThat(LiveOperationsService.map(row(null, null, NOW.plus(Duration.ofHours(1))), NOW).gpsState()).isEqualTo("NO_GPS");
        LiveGuardResponse stale = LiveOperationsService.map(row(NOW.minus(Duration.ofMinutes(30)), null, NOW.minus(Duration.ofMinutes(10))), NOW);
        assertThat(stale.gpsState()).isEqualTo("STALE");
        assertThat(stale.latitude()).isNotNull();
        assertThat(stale.overrunning()).isTrue();
    }
}

package za.co.handyflow.platform.fleet.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Nightly 02:15 SAST: deletes GPS positions older than {@code fleet.telematics.position-retention-days}
 * (default 90). Positions are by far the highest-volume table in Fleet; events, which are what people
 * actually refer back to, are kept indefinitely. The delete is bounded per run so a first run on a
 * large backlog doesn't hold a long lock — it simply catches up over successive nights.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FleetTelematicsScheduler {

    private static final int MAX_DELETE_PER_RUN = 2_000_000;

    private final JdbcTemplate jdbc;

    @Value("${fleet.telematics.position-retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 15 2 * * *", zone = "Africa/Johannesburg")
    public void purgeOldPositions() {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(retentionDays, ChronoUnit.DAYS));
        int deleted = jdbc.update(
                "DELETE FROM fleet_telematics_positions WHERE id IN ("
                        + "SELECT id FROM fleet_telematics_positions WHERE recorded_at < ? LIMIT ?)",
                cutoff, MAX_DELETE_PER_RUN);
        log.info("Telematics position purge complete — retention={} days, deleted={}", retentionDays, deleted);
    }
}
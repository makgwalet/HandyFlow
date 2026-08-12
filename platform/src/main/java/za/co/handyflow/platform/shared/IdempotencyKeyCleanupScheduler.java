package za.co.handyflow.platform.shared;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Deletes idempotency_keys rows older than the retention window. Unlike
 * rate_limits (small rows, cleanup flagged as a nice-to-have in that
 * migration's own comment), this table stores full response bodies —
 * unbounded growth here is a real storage concern, not a minor one, so
 * cleanup is implemented now rather than deferred.
 * <p>
 * RETENTION WINDOW: 48 hours. An Idempotency-Key's whole purpose is
 * protecting against a client retrying a request shortly after a network
 * failure — there's no legitimate reason a client would replay the same
 * key days later expecting the original response back. 48 hours gives
 * generous headroom over any realistic retry window while keeping the
 * table from growing indefinitely.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyKeyCleanupScheduler {

    private final JdbcTemplate jdbc;

    private static final Duration RETENTION = Duration.ofHours(48);

    @Scheduled(cron = "0 15 3 * * *", zone = "Africa/Johannesburg") // daily at 03:15 SAST
    public void cleanupOldRecords() {
        Instant cutoff = Instant.now().minus(RETENTION);
        int deleted = jdbc.update(
                "DELETE FROM idempotency_keys WHERE created_at < ?",
                Timestamp.from(cutoff));
        if (deleted > 0) {
            log.info("IdempotencyKeyCleanupScheduler: deleted {} record(s) older than {}h",
                    deleted, RETENTION.toHours());
        }
    }
}
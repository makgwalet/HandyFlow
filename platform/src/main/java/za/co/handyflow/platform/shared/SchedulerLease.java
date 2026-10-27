package za.co.handyflow.platform.shared;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Database-backed lease so a {@code @Scheduled} sweep runs on exactly one
 * application instance at a time. No extra dependency: one row per lock name
 * in {@code scheduler_locks}, claimed with an atomic upsert that only succeeds
 * when the previous lease has expired. Uses the database clock, so instance
 * clock drift is irrelevant.
 * <p>
 * {@code maxHold} is a safety valve: if an instance dies mid-sweep, another
 * instance can take over once it elapses. Set it comfortably above the
 * sweep's normal runtime.
 * <p>
 * If the project already uses ShedLock, prefer that and delete this class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerLease {

    private final JdbcTemplate jdbc;
    private final String instanceId = UUID.randomUUID().toString();

    /** @return true if this instance acquired the lease and ran the task. */
    public boolean runExclusively(String lockName, Duration maxHold, Runnable task) {
        if (!tryAcquire(lockName, maxHold)) {
            log.info("[Scheduler] '{}' is held by another instance — skipping this run", lockName);
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            release(lockName);
        }
    }

    private boolean tryAcquire(String lockName, Duration maxHold) {
        int rows = jdbc.update("""
                INSERT INTO scheduler_locks (lock_name, locked_until, locked_by)
                VALUES (?, now() + (?::double precision * interval '1 second'), ?)
                ON CONFLICT (lock_name) DO UPDATE
                   SET locked_until = EXCLUDED.locked_until,
                       locked_by    = EXCLUDED.locked_by
                 WHERE scheduler_locks.locked_until < now()
                """, lockName, (double) maxHold.toSeconds(), instanceId);
        return rows == 1;
    }

    private void release(String lockName) {
        try {
            jdbc.update("UPDATE scheduler_locks SET locked_until = now() WHERE lock_name = ? AND locked_by = ?",
                    lockName, instanceId);
        } catch (Exception e) {
            log.warn("[Scheduler] Failed to release lease '{}' (it will expire on its own): {}", lockName, e.getMessage());
        }
    }
}
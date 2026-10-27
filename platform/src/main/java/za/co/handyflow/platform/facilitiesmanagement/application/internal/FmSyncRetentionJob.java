package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.facilitiesmanagement.domain.repository.FmSyncOpRepository;
import za.co.handyflow.platform.shared.SchedulerLease;

import java.time.Duration;
import java.time.Instant;

/** Forgets sync idempotency records after {@value #RETENTION_DAYS} days, longer than any device stays offline. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FmSyncRetentionJob {

    static final int RETENTION_DAYS = 90;

    private final FmSyncOpRepository syncOpRepository;
    private final SchedulerLease schedulerLease;

    @Scheduled(cron = "0 20 3 * * *")
    public void run() {
        schedulerLease.runExclusively("fm-sync-retention", Duration.ofMinutes(10), () -> {
            int removed = purge(Instant.now());
            log.info("[FM] Sync retention removed {} record(s)", removed);
        });
    }

    @Transactional
    public int purge(Instant now) {
        return syncOpRepository.deleteAppliedBefore(now.minus(Duration.ofDays(RETENTION_DAYS)));
    }
}

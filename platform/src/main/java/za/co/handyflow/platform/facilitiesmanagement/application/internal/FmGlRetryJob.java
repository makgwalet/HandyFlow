package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.SchedulerLease;

import java.time.Duration;

/**
 * Every 15 minutes, retries ledger postings that failed or never ran (for example because the application
 * stopped between commit and posting). Runs under {@link SchedulerLease} so only one instance does it.
 * Documents that exhaust {@link FmGlPostingService#MAX_AUTO_ATTEMPTS} stay FAILED and appear in the
 * ledger-exceptions list until someone fixes the cause and re-queues them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FmGlRetryJob {

    private static final String LOCK_NAME = "fm-gl-retry";
    private static final Duration LOCK_MAX_HOLD = Duration.ofMinutes(10);

    private final FmGlPostingService glPostingService;
    private final SchedulerLease schedulerLease;

    @Scheduled(cron = "0 */15 * * * *")
    public void run() {
        schedulerLease.runExclusively(LOCK_NAME, LOCK_MAX_HOLD, () -> {
            int attempted = glPostingService.retryDue();
            if (attempted > 0) {
                log.info("[FM] Ledger retry job attempted {} posting(s)", attempted);
            }
        });
    }
}
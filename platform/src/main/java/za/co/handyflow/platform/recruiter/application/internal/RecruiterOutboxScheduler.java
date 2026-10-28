package za.co.handyflow.platform.recruiter.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.SchedulerLease;

import java.time.Duration;

/** Sends queued candidate emails once a minute, on one application instance at a time. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecruiterOutboxScheduler {

    private final RecruiterOutboxService outbox;
    private final SchedulerLease lease;

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void sweep() {
        try {
            lease.runExclusively("recruiter-outbox", Duration.ofMinutes(5), () -> {
                int sent = outbox.processDue();
                if (sent > 0) log.info("Recruiter outbox sent {} email(s)", sent);
            });
        } catch (RuntimeException e) {
            log.error("Recruiter outbox sweep failed", e);
        }
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "Africa/Johannesburg")
    public void purge() {
        try {
            lease.runExclusively("recruiter-outbox-purge", Duration.ofMinutes(10), () -> log.info("Recruiter outbox purged {} old email(s)", outbox.purgeOld()));
        } catch (RuntimeException e) {
            log.error("Recruiter outbox purge failed", e);
        }
    }
}

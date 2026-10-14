package za.co.handyflow.platform.accountant.application.internal;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** Daily: makes the recurring-service runs that have come due, for every practice. Bills nothing. */
@Component
@RequiredArgsConstructor
public class ServiceScheduleScheduler {
    private static final Logger log = LoggerFactory.getLogger(ServiceScheduleScheduler.class);
    private final ServiceScheduleService schedules;

    @Scheduled(cron = "0 45 5 * * *", zone = "Africa/Johannesburg")
    public void run() {
        try {
            int made = schedules.generateDue(null, LocalDate.now());
            if (made > 0) log.info("ServiceScheduleScheduler: {} service run(s) now due", made);
        } catch (RuntimeException e) {
            log.error("ServiceScheduleScheduler failed", e);
        }
    }
}

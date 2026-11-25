package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.SchedulerLease;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/** The daily digest, on working days at 07:20 South African time, on one application instance at a time. */
@Component
@RequiredArgsConstructor
public class ComplianceReminderScheduler {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final SchedulerLease lease;
    private final ComplianceReminderRunner runner;

    @Scheduled(cron = "0 20 7 * * *", zone = "Africa/Johannesburg")
    public void daily() {
        lease.runExclusively("cs-daily-digest", Duration.ofMinutes(20), () -> runner.runAll(LocalDate.now(SAST)));
    }
}

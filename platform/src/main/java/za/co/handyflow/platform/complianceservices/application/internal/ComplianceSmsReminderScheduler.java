package za.co.handyflow.platform.complianceservices.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.shared.SchedulerLease;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/** Client SMS reminders, every day at 08:30 South African time, on one application instance at a time. */
@Component
@RequiredArgsConstructor
public class ComplianceSmsReminderScheduler {

    static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final SchedulerLease lease;
    private final ComplianceSmsReminderRunner runner;

    @Scheduled(cron = "0 30 8 * * *", zone = "Africa/Johannesburg")
    public void daily() {
        lease.runExclusively("cs-sms-reminders", Duration.ofMinutes(20), () -> runner.runAll(LocalDate.now(SAST)));
    }
}

package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** Runs the letter and goal reminders every morning, South African time. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkReminderScheduler {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final WorkReminderService service;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Johannesburg")
    public void run() {
        if (!notificationsEnabled) return;
        try {
            service.remindAll(LocalDate.now(SAST));
        } catch (RuntimeException e) {
            log.error("Letter and goal reminders failed: {}", e.getMessage());
        }
    }
}

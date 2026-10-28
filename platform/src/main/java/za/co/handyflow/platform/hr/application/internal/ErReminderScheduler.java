package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/** Runs the employee relations case reminders every morning, South African time. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErReminderScheduler {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final ErReminderService service;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Scheduled(cron = "0 55 7 * * *", zone = "Africa/Johannesburg")
    public void run() {
        if (!notificationsEnabled) return;
        try {
            service.remindAll(LocalDate.now(SAST));
        } catch (RuntimeException e) {
            log.error("Case reminders failed: {}", e.getMessage());
        }
    }
}

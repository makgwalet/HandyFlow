package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sends the saved-report reminders at seven every morning, South African time. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SavedReportScheduler {

    private final SavedReportService service;

    @Value("${app.notifications.enabled:true}")
    private boolean notificationsEnabled;

    @Scheduled(cron = "0 0 7 * * *", zone = "Africa/Johannesburg")
    public void run() {
        if (!notificationsEnabled) return;
        try {
            service.remindAll(service.todaySast());
        } catch (RuntimeException e) {
            log.error("Saved report reminders failed: {}", e.getMessage());
        }
    }
}

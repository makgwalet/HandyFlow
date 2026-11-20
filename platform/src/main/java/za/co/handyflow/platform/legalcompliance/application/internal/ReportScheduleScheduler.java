package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Daily at 07:34: runs every report schedule that has come due. Each is isolated, so one bad schedule never stops the others. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportScheduleScheduler {

    private final ReportScheduleService service;

    @Scheduled(cron = "0 34 7 * * *", zone = "Africa/Johannesburg")
    public void sweep() {
        try {
            int n = service.sweep();
            if (n > 0) log.info("[Legal/Compliance] Ran {} scheduled report(s)", n);
        } catch (Exception e) {
            log.error("[Legal/Compliance] Report schedule sweep failed: {}", e.getMessage(), e);
        }
    }
}

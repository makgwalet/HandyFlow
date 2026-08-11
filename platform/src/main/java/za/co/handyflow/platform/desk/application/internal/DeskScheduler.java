package za.co.handyflow.platform.desk.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Owns Desk Support's own scheduled maintenance jobs. Same reasoning as
 * ApScheduler in the ap module — see that class's Javadoc for the full
 * explanation of why this moved out of billing.BillingScheduler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeskScheduler {

    private final DeskService deskService;

    // ── Hourly — check SLA breaches ───────────────────────────────────────────
    @Scheduled(cron = "0 0 * * * *", zone = "Africa/Johannesburg")
    void checkDeskSlaBreach() {
        deskService.checkSlaBreaches();
    }
}
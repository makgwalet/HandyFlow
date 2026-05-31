package za.co.handyflow.platform.billing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.ap.application.internal.ApService;
import za.co.handyflow.platform.desk.application.internal.DeskService;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingScheduler {

    private final SubscriptionService subscriptionService;
    private final ApService apService;
    private final DeskService deskService;

    // ── Daily at 02:00 SAST — expire pilots ───────────────────────────────────
    @Scheduled(cron = "0 0 2 * * *", zone = "Africa/Johannesburg")
    void expirePilots() {
        log.info("BillingScheduler: checking expired pilot subscriptions");
        subscriptionService.suspendExpiredPilots();
    }

    // ── Daily at 02:30 SAST — suspend grace-expired tenants ───────────────────
    // WHY 02:30 and not 02:00? Runs after pilot expiry to avoid contention.
    // Grace period check: finds all PAST_DUE subscriptions where
    // past_due_since + grace_period_days < now → suspend them.
    @Scheduled(cron = "0 30 2 * * *", zone = "Africa/Johannesburg")
    void enforceGracePeriods() {
        log.info("BillingScheduler: checking grace period expirations");
        subscriptionService.suspendGraceExpired();
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Africa/Johannesburg")
    void markOverdueBills() {
        log.info("ApScheduler: checking for overdue bills");
        apService.markOverdueBills();
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Africa/Johannesburg")  // every hour
    void checkDeskSlaBreach() {
        deskService.checkSlaBreaches();
    }
}

package za.co.handyflow.platform.billing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Billing's own scheduled maintenance jobs — pilot expiry and grace-period
 * enforcement. Used to also trigger AP's overdue-bill sweep and Desk's
 * SLA-breach sweep via ApFacade/DeskFacade; those moved to ApScheduler and
 * DeskScheduler respectively (see their Javadoc) once ArchitectureVerificationTest's
 * second real run showed that edge was the root cause of most of the
 * platform's reported module cycles. This class now only does billing's
 * own work, which is what it should have been doing all along.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingScheduler {

    private final SubscriptionService subscriptionService;

    // ── Daily at 02:00 SAST — expire pilots ───────────────────────────────────
    @Scheduled(cron = "0 0 2 * * *", zone = "Africa/Johannesburg")
    void expirePilots() {
        log.info("BillingScheduler: checking expired pilot subscriptions");
        subscriptionService.suspendExpiredPilots();
    }

    // ── Daily at 02:30 SAST — suspend grace-expired tenants ───────────────────
    // WHY 02:30 and not 02:00? Runs after pilot expiry to avoid contention.
    // Grace period check: finds all PAST_DUE subscriptions where
    // past_due_since + grace_period_days < now -> suspend them.
    @Scheduled(cron = "0 30 2 * * *", zone = "Africa/Johannesburg")
    void enforceGracePeriods() {
        log.info("BillingScheduler: checking grace period expirations");
        subscriptionService.suspendGraceExpired();
    }
}
package za.co.handyflow.platform.billing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * WHY a scheduler for pilot expiry?
 *
 * Pilots don't expire by magic — something has to check.
 * This scheduler runs daily and suspends any pilot that
 * has passed its pilotEndsAt date.
 *
 * In production this would also trigger:
 * - Email notifications to the tenant
 * - Sales team CRM task creation
 * - In-app banner activation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingScheduler {

    private final SubscriptionService subscriptionService;

    // WHY cron? Runs at 2:00 AM every day.
    // Off-peak time — minimal user impact.
    @Scheduled(cron = "0 0 2 * * *")
    void expirePilots() {
        log.info("BillingScheduler: Checking for expired pilot subscriptions");
        subscriptionService.suspendExpiredPilots();
    }
}

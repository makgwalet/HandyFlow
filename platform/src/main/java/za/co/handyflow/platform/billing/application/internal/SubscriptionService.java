package za.co.handyflow.platform.billing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.billing.domain.model.Plan;
import za.co.handyflow.platform.billing.domain.model.Subscription;
import za.co.handyflow.platform.billing.domain.repository.PlanRepository;
import za.co.handyflow.platform.billing.domain.repository.SubscriptionRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository         planRepository;
    private final EmailService           emailService;

    // ── Create trial ──────────────────────────────────────────────────────────

    public void createTrialSubscription(TenantId tenantId, String ownerEmail) {
        if (subscriptionRepository.existsByTenantId(tenantId)) {
            log.warn("Trial subscription already exists for tenant={}", tenantId);
            return;
        }

        Plan essentialPlan = planRepository.findByName("ESSENTIAL")
                .orElseThrow(() -> new IllegalStateException(
                        "ESSENTIAL plan not found — check V5 migration ran correctly"));

        Subscription subscription = Subscription.createPilot(tenantId, essentialPlan);
        subscriptionRepository.save(subscription);
        log.info("Created 60-day pilot subscription for tenant={}", tenantId);
    }

    // ── Activate ──────────────────────────────────────────────────────────────

    @Transactional
    public void activatedSubscription(TenantId tenantId) {
        Subscription sub = findSubscription(tenantId);
        sub.activate();
        subscriptionRepository.save(sub);
        log.info("Activated subscription for tenant={}", tenantId);
    }

    // ── B5: Mark past due (payment missed) ────────────────────────────────────

    @Transactional
    public void markPastDue(TenantId tenantId, String ownerEmail, String tenantName) {
        Subscription sub = findSubscription(tenantId);
        if (sub.getStatus().name().equals("PAST_DUE")
                || sub.getStatus().name().equals("SUSPENDED")) {
            return; // already past due or suspended
        }
        sub.markPastDue();
        subscriptionRepository.save(sub);
        log.warn("Marked PAST_DUE for tenant={} grace={}d",
                tenantId, sub.getGracePeriodDays());

        // Send grace period warning email
        try {
            String subject = "⚠️ HandyFlow payment overdue — " +
                    sub.getGracePeriodDays() + " days to pay before access is suspended";
            String html = gracePeriodEmail(tenantName, ownerEmail,
                    sub.getGracePeriodDays());
            emailService.send(ownerEmail, subject, html);
        } catch (Exception e) {
            log.error("Failed to send past-due email to {}: {}", ownerEmail, e.getMessage());
        }
    }

    // ── B5: Suspend grace-expired tenants (runs daily) ────────────────────────

    @Transactional
    public void suspendGraceExpired() {
        // Find all PAST_DUE subscriptions where grace period has elapsed
        // Use 7-day cutoff — any PAST_DUE older than 7 days is grace-expired
        Instant cutoff = java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS);
        List<Subscription> pastDue = subscriptionRepository.findPastDueOlderThan(cutoff);

        pastDue.stream()
                .forEach(sub -> {
                    sub.suspend();
                    subscriptionRepository.save(sub);
                    log.warn("SUSPENDED tenant={} — grace period expired", sub.getTenantId());

                    // Notify tenant
                    try {
                        notifySuspended(sub);
                    } catch (Exception e) {
                        log.error("Failed to send suspension email for tenant={}: {}",
                                sub.getTenantId(), e.getMessage());
                    }
                });
    }

    // ── B5: Reinstate after payment ───────────────────────────────────────────

    @Transactional
    public void reinstate(TenantId tenantId, String ownerEmail, String tenantName) {
        Subscription sub = findSubscription(tenantId);
        sub.reinstate();
        subscriptionRepository.save(sub);
        log.info("Reinstated subscription for tenant={}", tenantId);

        try {
            String subject = "✅ HandyFlow access restored — payment received";
            String html = reinstatedEmail(tenantName);
            emailService.send(ownerEmail, subject, html);
        } catch (Exception e) {
            log.error("Failed to send reinstatement email: {}", e.getMessage());
        }
    }

    // ── Existing: expire pilots ───────────────────────────────────────────────

    @Transactional
    public void suspendExpiredPilots() {
        var expired = subscriptionRepository.findExpiredPilots(Instant.now());
        expired.forEach(sub -> {
            sub.suspend();
            subscriptionRepository.save(sub);
            log.info("Suspended expired pilot for tenant={}", sub.getTenantId());
        });
        if (!expired.isEmpty()) {
            log.info("Suspended {} expired pilot subscriptions", expired.size());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Subscription findSubscription(TenantId tenantId) {
        return subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "No subscription found for tenant: " + tenantId));
    }

    private void notifySuspended(Subscription sub) {
        // Fetch tenant email via JDBC in the scheduler — here we just log
        // The scheduler (BillingScheduler) fetches emails and calls this
        log.info("Suspension notification queued for tenant={}", sub.getTenantId());
    }

    private String gracePeriodEmail(String tenantName, String email, int graceDays) {
        return """
            <p>Hi,</p>
            <p>Your HandyFlow payment for <strong>%s</strong> is overdue.</p>
            <p>You have <strong>%d days</strong> to settle your account before
               access to all modules is suspended.</p>
            <p>
              <a href="https://app.handyflow.co.za/billing"
                 style="background:#1B3A6B;color:white;padding:12px 24px;
                        border-radius:8px;text-decoration:none;
                        font-weight:bold;display:inline-block">
                Pay now
              </a>
            </p>
            <p>If you've already paid, please allow 24 hours for processing.</p>
            """.formatted(tenantName, graceDays);
    }

    private String reinstatedEmail(String tenantName) {
        return """
            <p>Hi,</p>
            <p>Great news — your payment for <strong>%s</strong> has been received
               and your HandyFlow access has been fully restored.</p>
            <p>
              <a href="https://app.handyflow.co.za"
                 style="background:#0D9488;color:white;padding:12px 24px;
                        border-radius:8px;text-decoration:none;
                        font-weight:bold;display:inline-block">
                Back to HandyFlow
              </a>
            </p>
            """.formatted(tenantName);
    }
}

package za.co.handyflow.platform.insurancebrokerage.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.insurancebrokerage.domain.model.InsBrokPolicy;
import za.co.handyflow.platform.insurancebrokerage.domain.repository.InsBrokPolicyRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Daily sweep, same two-notification shape {@code InsNotificationScheduler}
 * (internal `insurance` module, Increment 8a) already uses: policies
 * expiring within 30 days (reminded once/day via {@code
 * expiryReminderSentAt}), and policies past expiry with no renewal
 * (auto-marked EXPIRED). Follows {@code FacilityNotificationScheduler}/
 * {@code FmNotificationScheduler}'s own confirmed real
 * {@code NotificationService}/{@code NotificationRequest}/{@code
 * TenantAdminRecipients} call shape (builder-style request, recipients
 * resolved via {@code TenantAdminRecipients.resolveTenantAdmins()},
 * skip silently if no admins resolve) — NOT a guessed convenience method
 * (no {@code notifyTenantAdmins()} exists directly on
 * {@code NotificationService} itself).
 * <p>
 * CRON SLOT — UNCONFIRMED PLACEHOLDER, flagged not guessed: proposed at
 * <b>10:30 SAST</b>, immediately after {@code InsNotificationScheduler}'s
 * own proposed 10:15 SAST slot — kept in the same 15-minute-stagger
 * chain the rest of this engagement's schedulers follow, but that chain
 * has never been independently re-verified against a live checkout this
 * session either (same standing caveat carried in the handoff doc, §3
 * item 5). Confirm against the real {@code @Scheduled} cron values in
 * the checkout before relying on this not colliding with another
 * module's own sweep.
 * <p>
 * Requires two new {@code NotificationType} constants —
 * {@code INSURANCEBROKERAGE_POLICY_EXPIRING} and
 * {@code INSURANCEBROKERAGE_POLICY_EXPIRED} — see the accompanying
 * {@code InsuranceBrokerage-NotificationType-patch-instructions.md}.
 * Deliberately NOT reusing `insurance`'s own {@code
 * INSURANCE_POLICY_EXPIRING}/{@code INSURANCE_POLICY_EXPIRED} constants
 * — those two modules are independently subscribable (see package-info),
 * so their notification streams stay distinguishable too.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsBrokNotificationScheduler {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");
    private static final int EXPIRY_WARNING_DAYS = 30;

    private final InsBrokPolicyRepository policyRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 30 10 * * *", zone = "Africa/Johannesburg") // 10:30 SAST daily — see class Javadoc re: placeholder status
    @Transactional
    public void sweep() {
        log.info("[InsuranceBrokerage] Policy notification sweep starting");
        sendExpiringReminders();
        markPastExpiryAsExpired();
        log.info("[InsuranceBrokerage] Policy notification sweep complete");
    }

    private void sendExpiringReminders() {
        LocalDate today = LocalDate.now(SAST);
        LocalDate warningDate = today.plusDays(EXPIRY_WARNING_DAYS);
        Instant todayStart = today.atStartOfDay(SAST).toInstant();

        List<InsBrokPolicy> expiring = policyRepository.findExpiringForReminder(today, warningDate, todayStart);
        for (InsBrokPolicy policy : expiring) {
            try {
                notifyExpiring(policy);
                policy.markExpiryReminderSent();
                policyRepository.save(policy);
            } catch (Exception e) {
                log.error("[InsuranceBrokerage] Failed to send expiry reminder for policy={}: {}",
                        policy.getId(), e.getMessage(), e);
            }
        }
        log.info("[InsuranceBrokerage] {} expiry reminder(s) sent", expiring.size());
    }

    private void markPastExpiryAsExpired() {
        LocalDate today = LocalDate.now(SAST);
        List<InsBrokPolicy> pastExpiry = policyRepository.findPastExpiryNotRenewed(today);
        for (InsBrokPolicy policy : pastExpiry) {
            try {
                policy.markExpired();
                policyRepository.save(policy);
                notifyExpired(policy);
            } catch (Exception e) {
                log.error("[InsuranceBrokerage] Failed to auto-expire policy={}: {}",
                        policy.getId(), e.getMessage(), e);
            }
        }
        log.info("[InsuranceBrokerage] {} policy(ies) auto-marked EXPIRED", pastExpiry.size());
    }

    private void notifyExpiring(InsBrokPolicy policy) {
        TenantId tenantId = TenantId.of(policy.getTenantId());
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.INSURANCEBROKERAGE_POLICY_EXPIRING)
                .title("Client policy expiring soon: " + safeNumber(policy))
                .message("Policy " + safeNumber(policy) + " for client " + policy.getClientId()
                        + " expires on " + policy.getExpiryDate() + ".")
                .actionUrl("/insurancebrokerage/policies/" + policy.getId())
                .sourceModule("insurancebrokerage")
                .sourceEntityId(policy.getId().toString())
                .recipients(recipients)
                .build());
    }

    private void notifyExpired(InsBrokPolicy policy) {
        TenantId tenantId = TenantId.of(policy.getTenantId());
        List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (recipients.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.INSURANCEBROKERAGE_POLICY_EXPIRED)
                .title("Client policy EXPIRED unrenewed: " + safeNumber(policy))
                .message("Policy " + safeNumber(policy) + " for client " + policy.getClientId()
                        + " expired on " + policy.getExpiryDate() + " with no renewal recorded.")
                .actionUrl("/insurancebrokerage/policies/" + policy.getId())
                .sourceModule("insurancebrokerage")
                .sourceEntityId(policy.getId().toString())
                .recipients(recipients)
                .build());
    }

    private String safeNumber(InsBrokPolicy policy) {
        return policy.getPolicyNumber() != null ? policy.getPolicyNumber() : policy.getId().toString();
    }
}

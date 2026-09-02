package za.co.handyflow.platform.insurance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.insurance.domain.model.InsPolicy;
import za.co.handyflow.platform.insurance.domain.repository.InsPolicyRepository;
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
 * Daily sweep — proposed slot 10:15, Africa/Johannesburg, following this
 * codebase's own 15-minute scheduler stagger convention: confirmed chain
 * so far is Training 08:15 -> TrainProv 08:30 -> Facilities 08:45 -> ...
 * -> Agriculture 09:30 -> Agriculture-Crops 09:45. LegalPractice
 * (Module 7, immediately before this one) also has its own scheduler
 * ({@code LpNotificationScheduler}, referenced by
 * {@code LpMatterKeyDateRepository}'s own Javadoc) whose exact cron time
 * was NOT directly re-confirmed this session — 10:15 assumes it landed on
 * 10:00. FLAGGED, NOT GUESSED SILENTLY: check for a collision against
 * whatever cron {@code LpNotificationScheduler} actually uses before
 * deploying, and adjust this constant if needed.
 * <p>
 * Two independent sweeps, both cross-tenant then grouped by {@code
 * TenantId} — same convention as every other plain/embedded-TenantId
 * module's own scheduler ({@code AgNotificationScheduler} et al.):
 * <ol>
 *   <li>Policies expiring within {@link #EXPIRY_LOOKAHEAD_DAYS} days —
 *   WARNING, reminded once per day while in the window.</li>
 *   <li>Policies whose expiry has already passed with no renewal
 *   recorded — auto-marked EXPIRED and raised CRITICAL, same
 *   auto-expire-then-alert shape {@code TrainProvNotificationScheduler}/
 *   {@code TrainingNotificationScheduler} already use for certificates.</li>
 * </ol>
 * Requires two new {@code NotificationType} constants — see the
 * accompanying {@code Insurance-NotificationType-patch-instructions.md}.
 * <p>
 * ASSUMES {@code @EnableScheduling} IS ALREADY ON somewhere in this app —
 * not re-verified here, same standing assumption every prior scheduler in
 * this engagement has made.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InsNotificationScheduler {

    private static final int EXPIRY_LOOKAHEAD_DAYS = 30;
    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final InsPolicyRepository policyRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 15 10 * * *", zone = "Africa/Johannesburg")
    @Transactional
    public void runDailySweep() {
        int expired = sweepExpiredPolicies();
        int reminded = sweepExpiringSoon();
        log.info("[Insurance] Daily sweep complete — {} policies auto-expired, {} expiring-soon reminders sent", expired, reminded);
    }

    private int sweepExpiredPolicies() {
        LocalDate today = LocalDate.now(SAST);
        List<InsPolicy> expired = policyRepository.findExpiredAcrossTenants(today);
        int sent = 0;
        for (InsPolicy policy : expired) {
            try {
                policy.markExpired();
                policyRepository.save(policy);
                notify(policy, NotificationType.INSURANCE_POLICY_EXPIRED, "Insurance policy expired",
                        "Policy " + policy.getPolicyNumber() + " (" + policy.getInsurerName() + ") expired on "
                                + policy.getExpiryDate() + " with no renewal recorded.");
                sent++;
            } catch (Exception e) {
                log.error("[Insurance] Failed to process expiry for policy={}: {}", policy.getId(), e.getMessage(), e);
            }
        }
        return sent;
    }

    private int sweepExpiringSoon() {
        LocalDate today = LocalDate.now(SAST);
        LocalDate lookahead = today.plusDays(EXPIRY_LOOKAHEAD_DAYS);
        Instant sinceMidnight = today.atStartOfDay(SAST).toInstant();
        List<InsPolicy> expiringSoon = policyRepository.findExpiringSoonNotYetRemindedTodayAcrossTenants(today, lookahead, sinceMidnight);
        int sent = 0;
        for (InsPolicy policy : expiringSoon) {
            try {
                notify(policy, NotificationType.INSURANCE_POLICY_EXPIRING, "Insurance policy expiring soon",
                        "Policy " + policy.getPolicyNumber() + " (" + policy.getInsurerName() + ") expires "
                                + policy.getExpiryDate() + ".");
                policy.markExpiryReminderSent();
                policyRepository.save(policy);
                sent++;
            } catch (Exception e) {
                log.error("[Insurance] Failed to send expiring-soon reminder for policy={}: {}", policy.getId(), e.getMessage(), e);
            }
        }
        return sent;
    }

    private void notify(InsPolicy policy, NotificationType type, String title, String message) {
        TenantId tenantId = policy.getTenantId();
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) return;
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(type)
                .recipients(admins)
                .title(title)
                .message(message)
                .actionUrl("/insurance/policies/" + policy.getId())
                .sourceModule("insurance")
                .sourceEntityId(policy.getId().toString())
                .build());
    }
}

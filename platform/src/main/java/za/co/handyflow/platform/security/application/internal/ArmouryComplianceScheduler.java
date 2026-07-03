// security/application/internal/ArmouryComplianceScheduler.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationSeverity;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.security.domain.model.Armoury;
import za.co.handyflow.platform.security.domain.repository.ArmouryRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ArmouryComplianceScheduler — nightly check for expiring SAPS firearm licenses.
 *
 * MIGRATION NOTE (2026): "Email delivery is log-only until per-tenant admin
 * email is wired" is exactly the gap TenantAdminRecipients closes. A firearm
 * with an expired SAPS license is a hard compliance issue (Armoury.issue()
 * already hard-blocks on it) — supervisors need to know about this before it
 * blocks an issue attempt during a shift change, not discover it then. This
 * now delivers IN_APP + EMAIL to resolved tenant admins.
 *
 * Runs at 07:15 daily — five minutes after PsiraComplianceScheduler (07:00)
 * and GuardScreeningService.checkScreeningExpiry() (06:30), so all three
 * compliance checks land in the same morning batch without competing for DB
 * connections at the exact same second.
 *
 * WHY 30 days warning, same as PSiRA?
 * SAPS firearm license renewal has a similarly long lead time — 30 days
 * gives the operator enough runway to submit paperwork before the license
 * lapses and the firearm becomes legally unissuable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArmouryComplianceScheduler {

    private static final int WARN_DAYS_BEFORE = 30;

    private final ArmouryRepository armouryRepository;
    private final SiteRepository    siteRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 15 7 * * *")
    public void checkLicenseExpiry() {
        LocalDate today    = LocalDate.now();
        LocalDate warnDate = today.plusDays(WARN_DAYS_BEFORE);

        List<UUID> tenantIds = siteRepository.findDistinctActiveTenantIds();

        for (UUID tenantId : tenantIds) {
            try {
                checkForTenant(TenantId.of(tenantId), warnDate);
            } catch (Exception e) {
                log.error("[Security] Armoury compliance check failed for tenant={}: {}",
                        tenantId, e.getMessage(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public void checkForTenant(TenantId tenantId, LocalDate warnDate) {
        List<Armoury> expiringSoon = armouryRepository.findLicenseExpiringBy(tenantId, warnDate);
        if (expiringSoon.isEmpty()) return;

        List<Armoury> expired = expiringSoon.stream()
                .filter(Armoury::isLicenseExpired)
                .toList();
        List<Armoury> expiringNotYetExpired = expiringSoon.stream()
                .filter(a -> !a.isLicenseExpired())
                .toList();

        if (!expired.isEmpty()) {
            log.warn("[Security] {} firearm(s) with EXPIRED SAPS license for tenant={}",
                    expired.size(), tenantId.getValue());
        }
        if (!expiringNotYetExpired.isEmpty()) {
            log.info("[Security] {} firearm(s) with SAPS license expiring within {} days for tenant={}",
                    expiringNotYetExpired.size(), WARN_DAYS_BEFORE, tenantId.getValue());
        }

        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Security] Armoury compliance issues found for tenant={} but no admin recipients "
                            + "could be resolved. Expired: {} Expiring soon: {}",
                    tenantId.getValue(), expired.size(), expiringNotYetExpired.size());
            return;
        }

        boolean anyExpired = !expired.isEmpty();
        NotificationType type = anyExpired
                ? NotificationType.FIREARM_LICENSE_EXPIRED
                : NotificationType.FIREARM_LICENSE_EXPIRING_SOON;

        String title = anyExpired
                ? expired.size() + " firearm(s) with EXPIRED SAPS license"
                : expiringNotYetExpired.size() + " firearm(s) with SAPS license expiring within "
                + WARN_DAYS_BEFORE + " days";

        String message = buildSummary(expired, expiringNotYetExpired);

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(type)
                .severity(anyExpired ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING)
                .title(title)
                .message(message)
                .actionUrl("/security/armoury")
                .sourceModule("security")
                .recipients(admins)
                .build());

        log.info("[Security] Armoury compliance alert sent tenant={} expired={} expiring={}",
                tenantId.getValue(), expired.size(), expiringNotYetExpired.size());
    }

    private String buildSummary(List<Armoury> expired, List<Armoury> expiring) {
        StringBuilder sb = new StringBuilder();
        if (!expired.isEmpty()) {
            sb.append("Expired: ");
            sb.append(expired.stream()
                    .map(a -> a.getFirearmSerial() + " (" + a.getLicenseExpiry() + ")")
                    .collect(Collectors.joining(", ")));
        }
        if (!expiring.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("Expiring soon: ");
            sb.append(expiring.stream()
                    .map(a -> a.getFirearmSerial() + " (" + a.getLicenseExpiry() + ")")
                    .collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }
}
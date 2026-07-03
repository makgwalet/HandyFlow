// security/application/internal/PsiraComplianceScheduler.java

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
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PsiraComplianceScheduler — nightly check for expiring or expired PSiRA registrations.
 *
 * MIGRATION NOTE (2026): previously called EmailService.send() directly against
 * an admin email that resolveAdminEmail() always returned null for — meaning
 * every PSiRA compliance breach found by this scheduler was logged and then
 * silently dropped. It now routes through NotificationService, which:
 *   1. Writes an IN_APP row for each resolved tenant admin (visible in the bell,
 *      which the direct-email approach could never do), and
 *   2. Sends EMAIL via the same pipeline, using TenantAdminRecipients instead
 *      of the dead-end resolveAdminEmail() TODO.
 *
 * Severity: CRITICAL if any guard's PSiRA has already expired (this is a
 * live regulatory violation the moment that guard is scheduled), WARNING if
 * only expiring-soon guards were found.
 *
 * Runs at 07:00 daily — see class-level rationale below for WHY 07:00 / WHY
 * 30 days, both unchanged from the original.
 *
 * WHY 07:00 and not midnight?
 * Compliance alerts should land in an inbox at the start of the workday, not
 * at midnight when no one will action them.
 *
 * WHY 30 days warning?
 * PSiRA renewal takes 5–10 business days minimum. 30 days gives enough lead
 * time to submit renewal paperwork without disrupting shift scheduling.
 *
 * PRODUCTION NOTE: Use Quartz JDBC JobStore in multi-instance deployments to
 * prevent duplicate notifications from multiple app instances running
 * simultaneously.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PsiraComplianceScheduler {

    private static final int WARN_DAYS_BEFORE = 30;

    private final GuardRepository guardRepository;
    private final SiteRepository  siteRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 0 7 * * *")
    public void checkPsiraExpiry() {
        LocalDate today    = LocalDate.now();
        LocalDate warnDate = today.plusDays(WARN_DAYS_BEFORE);

        log.info("[Security] PSiRA compliance check starting date={}", today);

        List<UUID> tenantIds = siteRepository.findDistinctActiveTenantIds();
        int totalAlerts = 0;

        for (UUID tenantId : tenantIds) {
            try {
                int alerts = checkForTenant(TenantId.of(tenantId), today, warnDate);
                totalAlerts += alerts;
            } catch (Exception ex) {
                log.error("[Security] PSiRA check failed for tenant={}: {}", tenantId, ex.getMessage(), ex);
            }
        }

        log.info("[Security] PSiRA compliance check complete — {} alerts sent", totalAlerts);
    }

    @Transactional(readOnly = true)
    public int checkForTenant(TenantId tenantId, LocalDate today, LocalDate warnDate) {
        List<Guard> allGuards = guardRepository.findAllActive(
                tenantId, org.springframework.data.domain.Pageable.ofSize(500)).getContent();

        List<Guard> expiredGuards = allGuards.stream()
                .filter(g -> g.getPsiraExpiryDate() != null && g.getPsiraExpiryDate().isBefore(today))
                .collect(Collectors.toList());

        List<Guard> expiringGuards = allGuards.stream()
                .filter(g -> g.getPsiraExpiryDate() != null
                        && !g.getPsiraExpiryDate().isBefore(today)
                        && !g.getPsiraExpiryDate().isAfter(warnDate))
                .collect(Collectors.toList());

        if (expiredGuards.isEmpty() && expiringGuards.isEmpty()) {
            return 0;
        }

        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Security] PSiRA compliance issues found for tenant={} but no admin recipients "
                            + "could be resolved. Expired: {} Expiring soon: {}",
                    tenantId.getValue(), expiredGuards.size(), expiringGuards.size());
            return expiredGuards.size() + expiringGuards.size();
        }

        boolean anyExpired = !expiredGuards.isEmpty();
        NotificationType type = anyExpired ? NotificationType.PSIRA_EXPIRED : NotificationType.PSIRA_EXPIRING_SOON;

        String title = anyExpired
                ? expiredGuards.size() + " guard(s) with EXPIRED PSiRA registration"
                : expiringGuards.size() + " guard(s) with PSiRA expiring within " + WARN_DAYS_BEFORE + " days";

        String message = buildSummary(expiredGuards, expiringGuards);

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(type)
                .severity(anyExpired ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING)
                .title(title)
                .message(message)
                .actionUrl("/security/guards")
                .sourceModule("security")
                .recipients(admins)
                .build());

        log.info("[Security] PSiRA alert sent tenant={} expired={} expiring={}",
                tenantId.getValue(), expiredGuards.size(), expiringGuards.size());

        return expiredGuards.size() + expiringGuards.size();
    }

    private String buildSummary(List<Guard> expired, List<Guard> expiring) {
        StringBuilder sb = new StringBuilder();
        if (!expired.isEmpty()) {
            sb.append("Expired: ");
            sb.append(expired.stream()
                    .map(g -> g.getFullName() + " (" + g.getPsiraExpiryDate() + ")")
                    .collect(Collectors.joining(", ")));
        }
        if (!expiring.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("Expiring soon: ");
            sb.append(expiring.stream()
                    .map(g -> g.getFullName() + " (" + g.getPsiraExpiryDate() + ")")
                    .collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }
}
package za.co.handyflow.platform.compliancetender.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceDeadline;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceRegistration;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceDeadlineRepository;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceRegistrationRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationSeverity;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ComplianceExpiryScheduler — nightly check for expiring/expired compliance
 * registrations and due compliance deadlines. Same shape as
 * security.PsiraComplianceScheduler (07:00 daily, 30-day warning window,
 * routes through NotificationService — not a new delivery mechanism), with
 * one structural difference: PsiraComplianceScheduler iterates tenant by
 * tenant (via SiteRepository.findDistinctActiveTenantIds()) because Guard
 * records don't have a convenient cross-tenant sweep query. This module's
 * two repositories already expose cross-tenant sweeps
 * (findAllActiveWithExpiryAcrossTenants / findAllPendingDueAcrossTenants),
 * so this scheduler fetches once and groups by tenant in memory instead —
 * fewer queries, same end result.
 * <p>
 * WHY 07:00 and 30 days: same reasoning as PsiraComplianceScheduler — an
 * inbox alert at the start of the workday, and enough lead time to act on
 * a renewal (CIPC/SARS/PSIRA/CSD/cidb renewals all take real processing
 * time) without disrupting anything time-sensitive.
 * <p>
 * PRODUCTION NOTE (same as PsiraComplianceScheduler): use Quartz JDBC
 * JobStore in multi-instance deployments to prevent duplicate
 * notifications from multiple app instances running simultaneously.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceExpiryScheduler {

    private static final int WARN_DAYS_BEFORE = 30;

    private final ComplianceRegistrationRepository registrationRepository;
    private final ComplianceDeadlineRepository deadlineRepository;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 0 7 * * *")
    public void checkComplianceExpiry() {
        LocalDate today    = LocalDate.now();
        LocalDate warnDate = today.plusDays(WARN_DAYS_BEFORE);

        log.info("[Compliance] Expiry check starting date={}", today);

        List<ComplianceRegistration> dueRegistrations = registrationRepository.findAllActiveWithExpiryAcrossTenants(warnDate);
        List<ComplianceDeadline> dueDeadlines = deadlineRepository.findAllPendingDueAcrossTenants(warnDate);

        Map<UUID, List<ComplianceRegistration>> registrationsByTenant =
                dueRegistrations.stream().collect(Collectors.groupingBy(r -> r.getTenantId().getValue()));
        Map<UUID, List<ComplianceDeadline>> deadlinesByTenant =
                dueDeadlines.stream().collect(Collectors.groupingBy(d -> d.getTenantId().getValue()));

        Set<UUID> allTenantIds = new HashSet<>();
        allTenantIds.addAll(registrationsByTenant.keySet());
        allTenantIds.addAll(deadlinesByTenant.keySet());

        int totalAlerts = 0;
        for (UUID tenantId : allTenantIds) {
            try {
                totalAlerts += checkForTenant(TenantId.of(tenantId), today,
                        registrationsByTenant.getOrDefault(tenantId, List.of()),
                        deadlinesByTenant.getOrDefault(tenantId, List.of()));
            } catch (Exception ex) {
                log.error("[Compliance] Expiry check failed for tenant={}: {}", tenantId, ex.getMessage(), ex);
            }
        }

        log.info("[Compliance] Expiry check complete — {} alerts sent", totalAlerts);
    }

    @Transactional(readOnly = true)
    int checkForTenant(TenantId tenantId, LocalDate today,
                       List<ComplianceRegistration> dueRegistrations, List<ComplianceDeadline> dueDeadlines) {
        List<ComplianceRegistration> expiredRegs = dueRegistrations.stream()
                .filter(r -> r.getExpiryDate().isBefore(today)).toList();
        List<ComplianceRegistration> expiringRegs = dueRegistrations.stream()
                .filter(r -> !r.getExpiryDate().isBefore(today)).toList();
        // Deadlines are all still PENDING with dueDate <= warnDate by construction of the
        // query — no expired/expiring split needed the way registrations have (a deadline
        // that's actually passed becomes MISSED via markMissed(), a separate concern from
        // this notification sweep, matching how PsiraComplianceScheduler also doesn't
        // itself mutate Guard records, only reads and alerts).

        if (expiredRegs.isEmpty() && expiringRegs.isEmpty() && dueDeadlines.isEmpty()) return 0;

        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Compliance] Compliance issues found for tenant={} but no admin recipients could be "
                            + "resolved. Expired regs: {} Expiring regs: {} Due deadlines: {}",
                    tenantId.getValue(), expiredRegs.size(), expiringRegs.size(), dueDeadlines.size());
            return expiredRegs.size() + expiringRegs.size() + dueDeadlines.size();
        }

        int alertsSent = 0;
        if (!expiredRegs.isEmpty() || !expiringRegs.isEmpty()) {
            sendRegistrationAlert(tenantId, expiredRegs, expiringRegs, admins);
            alertsSent += expiredRegs.size() + expiringRegs.size();
        }
        if (!dueDeadlines.isEmpty()) {
            sendDeadlineAlert(tenantId, dueDeadlines, admins);
            alertsSent += dueDeadlines.size();
        }
        return alertsSent;
    }

    private void sendRegistrationAlert(TenantId tenantId, List<ComplianceRegistration> expired,
                                       List<ComplianceRegistration> expiring, List<Recipient> admins) {
        boolean anyExpired = !expired.isEmpty();
        NotificationType type = anyExpired
                ? NotificationType.COMPLIANCE_REGISTRATION_EXPIRED : NotificationType.COMPLIANCE_REGISTRATION_EXPIRING;

        String title = anyExpired
                ? expired.size() + " compliance registration(s) EXPIRED"
                : expiring.size() + " compliance registration(s) expiring within " + WARN_DAYS_BEFORE + " days";

        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(type)
                .severity(anyExpired ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING)
                .title(title)
                .message(buildRegistrationSummary(expired, expiring))
                .actionUrl("/compliance/registrations")
                .sourceModule("compliancetender")
                .recipients(admins)
                .build());

        log.info("[Compliance] Registration alert sent tenant={} expired={} expiring={}",
                tenantId.getValue(), expired.size(), expiring.size());
    }

    private void sendDeadlineAlert(TenantId tenantId, List<ComplianceDeadline> dueDeadlines, List<Recipient> admins) {
        notificationService.send(NotificationRequest.builder()
                .tenantId(tenantId)
                .type(NotificationType.COMPLIANCE_DEADLINE_DUE)
                .severity(NotificationSeverity.WARNING)
                .title(dueDeadlines.size() + " compliance deadline(s) due within " + WARN_DAYS_BEFORE + " days")
                .message(dueDeadlines.stream()
                        .map(d -> d.getDeadlineType() + " (" + d.getDueDate() + ")")
                        .collect(Collectors.joining(", ")))
                .actionUrl("/compliance/deadlines")
                .sourceModule("compliancetender")
                .recipients(admins)
                .build());

        log.info("[Compliance] Deadline alert sent tenant={} due={}", tenantId.getValue(), dueDeadlines.size());
    }

    private String buildRegistrationSummary(List<ComplianceRegistration> expired, List<ComplianceRegistration> expiring) {
        StringBuilder sb = new StringBuilder();
        if (!expired.isEmpty()) {
            sb.append("Expired: ");
            sb.append(expired.stream()
                    .map(r -> r.getAuthority() + " " + r.getRegistrationType() + " (" + r.getExpiryDate() + ")")
                    .collect(Collectors.joining(", ")));
        }
        if (!expiring.isEmpty()) {
            if (!sb.isEmpty()) sb.append(" | ");
            sb.append("Expiring soon: ");
            sb.append(expiring.stream()
                    .map(r -> r.getAuthority() + " " + r.getRegistrationType() + " (" + r.getExpiryDate() + ")")
                    .collect(Collectors.joining(", ")));
        }
        return sb.toString();
    }
}

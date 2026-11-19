package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryImpact;
import za.co.handyflow.platform.legalcompliance.domain.repository.RegulatoryImpactRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Daily sweep (06:50, five minutes after the obligation/DSAR sweep) for regulatory change impacts still awaiting a decision.
 * One notification per tenant per category. Like the other daily compliance sweeps it does not de-duplicate: reminding every
 * morning about something still overdue is intended.
 * Requires the two LEGALCOMPLIANCE_CHANGE_IMPACT_* constants in NotificationType (patched copy included in this chunk).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalComplianceChangeScheduler {

    private final RegulatoryImpactRepository impacts;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Value("${app.legalcompliance.change.due-soon-days:7}")
    private int dueSoonDays;

    @Scheduled(cron = "0 50 6 * * *")
    public void checkImpactDeadlines() {
        try {
            sweep();
        } catch (Exception e) {
            log.error("[Legal/Compliance] Regulatory change impact sweep failed: {}", e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public void sweep() {
        LocalDate today = LocalDate.now();
        List<RegulatoryImpact> pending = impacts.findPendingDueByAcrossTenants(today.plusDays(dueSoonDays));
        if (pending.isEmpty()) return;

        notifyAll(pending.stream().filter(i -> i.getDueDate().isBefore(today)).collect(Collectors.groupingBy(RegulatoryImpact::getTenantId)),
                NotificationType.LEGALCOMPLIANCE_CHANGE_IMPACT_OVERDUE, "overdue");
        notifyAll(pending.stream().filter(i -> !i.getDueDate().isBefore(today)).collect(Collectors.groupingBy(RegulatoryImpact::getTenantId)),
                NotificationType.LEGALCOMPLIANCE_CHANGE_IMPACT_DUE_SOON, "due within " + dueSoonDays + " days");
    }

    private void notifyAll(Map<TenantId, List<RegulatoryImpact>> byTenant, NotificationType type, String label) {
        byTenant.forEach((tenantId, list) -> {
            List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            if (admins.isEmpty()) {
                log.warn("[Legal/Compliance] {} regulatory change impact(s) {} for tenant={} but no admin recipients could be resolved",
                        list.size(), label, tenantId.getValue());
                return;
            }
            String message = list.stream().limit(10).map(i -> i.getReference() + " " + i.getTitle() + " (due " + i.getDueDate() + ")")
                    .collect(Collectors.joining("; ")) + (list.size() > 10 ? " … and " + (list.size() - 10) + " more" : "");
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId)
                    .type(type)
                    .title(list.size() + " regulatory change impact(s) " + label)
                    .message(message)
                    .actionUrl("/legalcompliance/changes")
                    .sourceModule("legalcompliance")
                    .recipients(admins)
                    .build());
            log.info("[Legal/Compliance] Change impact alert sent tenant={} count={} label={}", tenantId.getValue(), list.size(), label);
        });
    }
}

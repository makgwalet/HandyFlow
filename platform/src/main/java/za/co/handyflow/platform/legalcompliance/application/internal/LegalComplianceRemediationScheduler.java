package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.domain.model.Audit;
import za.co.handyflow.platform.legalcompliance.domain.model.AuditFinding;
import za.co.handyflow.platform.legalcompliance.domain.model.Capa;
import za.co.handyflow.platform.legalcompliance.domain.repository.AuditFindingRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.AuditRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.CapaRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Daily 07:02 sweep: tells tenant admins about audits past their planned end that are not finished, audit findings past their due date,
 * and corrective/preventive actions past their due date. Read-only; like the other daily sweeps it does not de-duplicate.
 * Requires LEGALCOMPLIANCE_AUDIT_OVERDUE / _AUDIT_FINDING_OVERDUE / _CAPA_OVERDUE in NotificationType (patched copy included).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalComplianceRemediationScheduler {

    private final AuditRepository audits;
    private final AuditFindingRepository auditFindings;
    private final CapaRepository capas;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 2 7 * * *")
    public void sweep() {
        LocalDate today = LocalDate.now();
        try {
            notifyAdmins(audits.findOverdueAcrossTenants(today), Audit::getTenantId, NotificationType.LEGALCOMPLIANCE_AUDIT_OVERDUE, "audit(s) past their planned end",
                    a -> a.getReference() + " " + a.getTitle() + " (planned end " + a.getPlannedEnd() + ")", "/legalcompliance/audits");
        } catch (Exception e) { log.error("[Legal/Compliance] Audit sweep failed: {}", e.getMessage(), e); }
        try {
            notifyAdmins(auditFindings.findOverdueAcrossTenants(today), AuditFinding::getTenantId, NotificationType.LEGALCOMPLIANCE_AUDIT_FINDING_OVERDUE, "audit finding(s) overdue",
                    f -> f.getReference() + " " + f.getSeverity() + " (due " + f.getDueDate() + ", owner " + f.getOwnerName() + ")", "/legalcompliance/audits");
        } catch (Exception e) { log.error("[Legal/Compliance] Audit finding sweep failed: {}", e.getMessage(), e); }
        try {
            notifyAdmins(capas.findOverdueAcrossTenants(today), Capa::getTenantId, NotificationType.LEGALCOMPLIANCE_CAPA_OVERDUE, "corrective action(s) overdue",
                    c -> c.getReference() + " " + c.getTitle() + " (due " + c.getDueDate() + ", owner " + c.getOwnerName() + ")", "/legalcompliance/capa");
        } catch (Exception e) { log.error("[Legal/Compliance] CAPA sweep failed: {}", e.getMessage(), e); }
    }

    private <T> void notifyAdmins(List<T> items, Function<T, TenantId> tenantOf, NotificationType type, String what, Function<T, String> line, String url) {
        Map<TenantId, List<T>> byTenant = items.stream().collect(Collectors.groupingBy(tenantOf));
        byTenant.forEach((tenantId, list) -> {
            List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            String title = list.size() + " " + what;
            if (admins.isEmpty()) {
                log.warn("[Legal/Compliance] {} for tenant={} but no admin recipients could be resolved", title, tenantId.getValue());
                return;
            }
            String message = list.stream().limit(10).map(line).collect(Collectors.joining("; ")) + (list.size() > 10 ? " … and " + (list.size() - 10) + " more" : "");
            notificationService.send(NotificationRequest.builder().tenantId(tenantId).type(type).title(title).message(message)
                    .actionUrl(url).sourceModule("legalcompliance").recipients(admins).build());
        });
    }
}

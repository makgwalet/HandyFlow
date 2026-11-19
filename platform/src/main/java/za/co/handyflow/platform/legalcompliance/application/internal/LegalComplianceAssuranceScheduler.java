package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.domain.model.Control;
import za.co.handyflow.platform.legalcompliance.domain.model.Finding;
import za.co.handyflow.platform.legalcompliance.domain.repository.ControlRepository;
import za.co.handyflow.platform.legalcompliance.domain.repository.FindingRepository;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Daily 06:58 sweep: tells tenant admins which active controls are due (or overdue) for testing and which findings are past their due date.
 * A control counts as due when its next-test date has passed, or when it has never been tested and was activated more than
 * ControlTestRules.FIRST_TEST_GRACE_DAYS days ago. Read-only; like the other daily sweeps it does not de-duplicate.
 * Requires the LEGALCOMPLIANCE_CONTROL_TEST_DUE / LEGALCOMPLIANCE_FINDING_OVERDUE constants in NotificationType (patched copy included).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalComplianceAssuranceScheduler {

    private final ControlRepository controls;
    private final FindingRepository findings;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 58 6 * * *")
    public void sweep() {
        try { controlsDue(); } catch (Exception e) { log.error("[Legal/Compliance] Control test sweep failed: {}", e.getMessage(), e); }
        try { findingsOverdue(); } catch (Exception e) { log.error("[Legal/Compliance] Finding sweep failed: {}", e.getMessage(), e); }
    }

    void controlsDue() {
        LocalDate today = LocalDate.now();
        Instant cutoff = today.minusDays(ControlTestRules.FIRST_TEST_GRACE_DAYS).atStartOfDay(ZoneId.of("Africa/Johannesburg")).toInstant();
        Map<TenantId, List<Control>> byTenant = controls.findTestDueAcrossTenants(today, cutoff).stream().collect(Collectors.groupingBy(Control::getTenantId));
        byTenant.forEach((tenantId, list) -> send(tenantId, NotificationType.LEGALCOMPLIANCE_CONTROL_TEST_DUE, list.size() + " control(s) due for testing",
                list.stream().limit(10).map(c -> c.getReference() + " " + c.getTitle() + (c.getNextTestDue() != null ? " (was due " + c.getNextTestDue() + ")" : " (never tested)")).collect(Collectors.joining("; "))
                        + (list.size() > 10 ? " … and " + (list.size() - 10) + " more" : ""), "/legalcompliance/testing"));
    }

    void findingsOverdue() {
        Map<TenantId, List<Finding>> byTenant = findings.findOverdueAcrossTenants(LocalDate.now()).stream().collect(Collectors.groupingBy(Finding::getTenantId));
        byTenant.forEach((tenantId, list) -> send(tenantId, NotificationType.LEGALCOMPLIANCE_FINDING_OVERDUE, list.size() + " finding(s) overdue",
                list.stream().limit(10).map(f -> f.getReference() + " " + f.getSeverity() + " (due " + f.getDueDate() + ", owner " + f.getOwnerName() + ")").collect(Collectors.joining("; "))
                        + (list.size() > 10 ? " … and " + (list.size() - 10) + " more" : ""), "/legalcompliance/findings"));
    }

    private void send(TenantId tenantId, NotificationType type, String title, String message, String url) {
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Legal/Compliance] {} for tenant={} but no admin recipients could be resolved", title, tenantId.getValue());
            return;
        }
        notificationService.send(NotificationRequest.builder().tenantId(tenantId).type(type).title(title).message(message)
                .actionUrl(url).sourceModule("legalcompliance").recipients(admins).build());
    }
}

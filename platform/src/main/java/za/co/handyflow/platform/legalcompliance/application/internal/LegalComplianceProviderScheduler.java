package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.domain.model.ProviderClient;
import za.co.handyflow.platform.legalcompliance.domain.repository.ProviderClientRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Daily 07:26 sweep, to the PROVIDER's tenant admins only: client reviews that are due or overdue (on the due date, then weekly).
 * Messages name the provider's own client labels and references, never anything from the client's records. Read-only.
 * Requires NotificationType.LEGALCOMPLIANCE_PROVIDER_ATTENTION (patched NotificationType included).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalComplianceProviderScheduler {

    private final ProviderClientRepository clients;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 26 7 * * *")
    public void sweep() {
        try { reviewsDue(); } catch (Exception e) { log.error("[Legal/Compliance] Provider review sweep failed: {}", e.getMessage(), e); }
    }

    void reviewsDue() {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Johannesburg"));
        List<ProviderClient> due = clients.findReviewDueAcrossTenants(today).stream().filter(c -> ProviderRules.reminderDue(c.getNextReviewDue(), today)).toList();
        Map<TenantId, List<ProviderClient>> byTenant = due.stream().collect(Collectors.groupingBy(ProviderClient::getTenantId));
        byTenant.forEach((tenantId, list) -> {
            List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            String title = list.size() + " client review(s) due or overdue";
            if (admins.isEmpty()) { log.warn("[Legal/Compliance] {} for tenant={} but no admin recipients could be resolved", title, tenantId.getValue()); return; }
            String message = list.stream().limit(10).map(c -> c.getReference() + " " + c.getClientName() + " (due " + c.getNextReviewDue() + ")").collect(Collectors.joining("; "))
                    + (list.size() > 10 ? " … and " + (list.size() - 10) + " more" : "");
            notificationService.send(NotificationRequest.builder().tenantId(tenantId).type(NotificationType.LEGALCOMPLIANCE_PROVIDER_ATTENTION).title(title).message(message)
                    .actionUrl("/legalcompliance/providers").sourceModule("legalcompliance").recipients(admins).build());
        });
    }
}

package za.co.handyflow.platform.legalcompliance.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.domain.model.Risk;
import za.co.handyflow.platform.legalcompliance.domain.repository.RiskRepository;
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
 * Daily 06:55 sweep: (1) lapses risk acceptances whose expiry date has passed (each in its own transaction, via RiskService, so one failure
 * does not block the rest), then (2) tells tenant admins about risks whose review is overdue. No de-duplication, like the other daily sweeps.
 * Requires the LEGALCOMPLIANCE_RISK_* constants in NotificationType (patched copy included).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegalComplianceRiskScheduler {

    private final RiskRepository risks;
    private final RiskService riskService;
    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    @Scheduled(cron = "0 55 6 * * *")
    public void sweep() {
        try { lapseAcceptances(); } catch (Exception e) { log.error("[Legal/Compliance] Risk acceptance sweep failed: {}", e.getMessage(), e); }
        try { notifyOverdueReviews(); } catch (Exception e) { log.error("[Legal/Compliance] Risk review sweep failed: {}", e.getMessage(), e); }
    }

    void lapseAcceptances() {
        List<Risk> expired = risks.findAcceptanceExpiredAcrossTenants(LocalDate.now());
        Map<TenantId, List<Risk>> byTenant = expired.stream().collect(Collectors.groupingBy(Risk::getTenantId));
        byTenant.forEach((tenantId, list) -> {
            for (Risk r : list) {
                try { riskService.lapseExpiredAcceptance(tenantId, r.getId()); }
                catch (Exception e) { log.error("[Legal/Compliance] Could not lapse acceptance of risk {}: {}", r.getId(), e.getMessage()); }
            }
            send(tenantId, NotificationType.LEGALCOMPLIANCE_RISK_ACCEPTANCE_EXPIRED, list.size() + " risk acceptance(s) expired",
                    list.stream().map(r -> r.getReference() + " " + r.getTitle()).collect(Collectors.joining("; ")));
        });
    }

    void notifyOverdueReviews() {
        Map<TenantId, List<Risk>> byTenant = risks.findReviewOverdueAcrossTenants(LocalDate.now()).stream().collect(Collectors.groupingBy(Risk::getTenantId));
        byTenant.forEach((tenantId, list) -> send(tenantId, NotificationType.LEGALCOMPLIANCE_RISK_REVIEW_OVERDUE, list.size() + " risk review(s) overdue",
                list.stream().limit(10).map(r -> r.getReference() + " " + r.getTitle() + " (review was due " + r.getNextReviewDate() + ")").collect(Collectors.joining("; "))
                        + (list.size() > 10 ? " … and " + (list.size() - 10) + " more" : "")));
    }

    private void send(TenantId tenantId, NotificationType type, String title, String message) {
        List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
        if (admins.isEmpty()) {
            log.warn("[Legal/Compliance] {} for tenant={} but no admin recipients could be resolved", title, tenantId.getValue());
            return;
        }
        notificationService.send(NotificationRequest.builder().tenantId(tenantId).type(type).title(title).message(message)
                .actionUrl("/legalcompliance/risks").sourceModule("legalcompliance").recipients(admins).build());
    }
}

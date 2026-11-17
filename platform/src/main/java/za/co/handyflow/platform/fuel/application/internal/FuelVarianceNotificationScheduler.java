package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.domain.repository.FuelSettingsRepository;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

/**
 * Tells a tenant's administrators when delivery variances outside their own tolerance have been raised, so they do not have
 * to open the Variances list to find out. Only tenants that have set a tolerance are scanned, and only tenants whose plan
 * still includes Fuel. A variance is raised once per delivery, so a tenant is told about each one once. The alert is a
 * pointer to the list: it says nothing about cause or fault, and nothing is written off.
 * If the notification fails the variances are already saved and still show in the list; the alert for that run is lost.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FuelVarianceNotificationScheduler {

    private final FuelSettingsRepository settings;
    private final FuelVarianceService variances;
    private final FeatureGuard featureGuard;
    private final NotificationService notifications;
    private final TenantAdminRecipients adminRecipients;
    private final FuelAlertRecipientService alertRecipients;

    @Value("${fuel.variance.alerts.enabled:true}")
    private boolean enabled = true;

    @Scheduled(cron = "0 15 7 * * *", zone = "Africa/Johannesburg")
    public void dailySweep() {
        if (!enabled) return;
        sweep();
        sweepOverdue();
    }

    /** Public so a test can run it. Tells administrators about variances left unresolved longer than the company's own number of days. Each variance once. */
    public int sweepOverdue() {
        int alerted = 0;
        for (var s : settings.findWithVarianceReviewDays()) {
            TenantId tenant = s.getTenantId();
            try {
                if (!featureGuard.hasModuleFor(tenant, "fuel")) continue;
                var overdue = variances.overdueNotAlerted(tenant, s.getVarianceReviewDays());
                if (overdue.isEmpty()) continue;
                List<Recipient> recipients = alertRecipients.recipientsFor(tenant, FuelAlertRecipientService.VARIANCE);
                if (recipients.isEmpty()) continue;
                int n = overdue.size();
                notifications.send(NotificationRequest.builder()
                        .tenantId(tenant)
                        .type(NotificationType.FUEL_VARIANCE_OVERDUE)
                        .title("Fuel: delivery variance still unresolved")
                        .message(n + " delivery variance" + (n == 1 ? " has" : "s have") + " been open for more than " + s.getVarianceReviewDays()
                                + " day" + (s.getVarianceReviewDays() == 1 ? "" : "s") + ". Review " + (n == 1 ? "it" : "them") + " in Fuel > Variances.")
                        .actionUrl("/fuel/variances")
                        .sourceModule("fuel")
                        .sourceEntityId(tenant.getValue().toString())
                        .recipients(recipients)
                        .build());
                variances.markAlerted(tenant, overdue.stream().map(v -> v.getId()).toList());
                alerted++;
            } catch (RuntimeException ex) {
                log.error("Fuel overdue variance sweep failed for tenant {}: {}", tenant, ex.getMessage());
            }
        }
        log.info("Fuel overdue variance sweep: tenants alerted={}", alerted);
        return alerted;
    }

    /** Public so a test can run it. Returns how many tenants were alerted. */
    public int sweep() {
        int alerted = 0;
        for (TenantId tenant : settings.findTenantsWithVarianceTolerance()) {
            try {
                if (!featureGuard.hasModuleFor(tenant, "fuel")) continue;
                int created = variances.raiseNew(tenant);
                if (created <= 0) continue;
                List<Recipient> recipients = alertRecipients.recipientsFor(tenant, FuelAlertRecipientService.VARIANCE);
                if (recipients.isEmpty()) continue;
                notifications.send(NotificationRequest.builder()
                        .tenantId(tenant)
                        .type(NotificationType.FUEL_VARIANCE_RAISED)
                        .title("Fuel: delivery variance to review")
                        .message(created + " delivery variance" + (created == 1 ? " is" : "s are")
                                + " outside your tolerance and need" + (created == 1 ? "s" : "") + " review in Fuel > Variances.")
                        .actionUrl("/fuel/variances")
                        .sourceModule("fuel")
                        .sourceEntityId(tenant.getValue().toString())
                        .recipients(recipients)
                        .build());
                alerted++;
            } catch (RuntimeException ex) {
                log.error("Fuel variance sweep failed for tenant {}: {}", tenant, ex.getMessage());
            }
        }
        log.info("Fuel variance sweep: tenants alerted={}", alerted);
        return alerted;
    }
}

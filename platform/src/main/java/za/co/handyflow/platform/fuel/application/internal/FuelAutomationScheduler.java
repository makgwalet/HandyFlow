package za.co.handyflow.platform.fuel.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.billing.FeatureGuard;
import za.co.handyflow.platform.fuel.domain.model.FuelPurchaseOrder;
import za.co.handyflow.platform.fuel.domain.repository.FuelAutomationRuleRepository;
import za.co.handyflow.platform.fuel.domain.repository.FuelSettingsRepository;
import za.co.handyflow.platform.fuel.dto.AutomationDtos.RunResult;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.*;

/**
 * Phase 6b: the daily automation run. For each tenant that still has Fuel: check its reorder rules (drafts only) and, if the tenant
 * switched it on, alert its administrators once about each overdue purchase order. Licence expiry is not repeated here: the compliance
 * reminder job already does it. An overdue order is marked alerted only after the notice has been sent, so a failed send is retried.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FuelAutomationScheduler {

    private final FuelAutomationRuleRepository rules;
    private final FuelSettingsRepository settings;
    private final FuelAutomationService automation;
    private final FeatureGuard featureGuard;
    private final NotificationService notifications;
    private final TenantAdminRecipients adminRecipients;

    @Value("${fuel.automation.enabled:true}")
    private boolean enabled = true;

    @Scheduled(cron = "0 30 6 * * *", zone = "Africa/Johannesburg")
    public void dailyRun() {
        if (!enabled) return;
        sweep();
    }

    /** Public so a test can run it. Returns how many notices were sent. */
    public int sweep() {
        Set<TenantId> tenants = new LinkedHashSet<>(rules.findTenantsWithEnabledRules());
        tenants.addAll(settings.findTenantsAlertingOverduePurchaseOrders());
        Set<TenantId> overdueTenants = new HashSet<>(settings.findTenantsAlertingOverduePurchaseOrders());
        int sent = 0;
        for (TenantId tenant : tenants) {
            try {
                if (!featureGuard.hasModuleFor(tenant, "fuel")) continue;
                RunResult r = automation.runRules(tenant);
                if (r.drafted() > 0) sent += notify(tenant, NotificationType.FUEL_REORDER_DRAFTED, "Fuel: reorder drafted",
                        r.drafted() + " purchase requisition" + (r.drafted() == 1 ? " was" : "s were") + " drafted automatically (" + String.join(", ", r.requisitionNumbers())
                                + "). Nothing has been submitted or ordered: review " + (r.drafted() == 1 ? "it" : "them") + " in Fuel > Purchase orders.", "/fuel/purchase-orders");
                if (overdueTenants.contains(tenant)) sent += alertOverdue(tenant);
            } catch (RuntimeException ex) {
                log.error("Fuel automation failed for tenant {}: {}", tenant, ex.getMessage());
            }
        }
        log.info("Fuel automation run: notices sent={}", sent);
        return sent;
    }

    private int alertOverdue(TenantId tenant) {
        List<FuelPurchaseOrder> late = automation.overdueOrders(tenant);
        if (late.isEmpty()) return 0;
        List<String> numbers = late.stream().map(FuelPurchaseOrder::getPoNumber).toList();
        int n = notify(tenant, NotificationType.FUEL_PO_OVERDUE, "Fuel: purchase order overdue",
                late.size() + " purchase order" + (late.size() == 1 ? " is" : "s are") + " past the expected date and not fully received: " + String.join(", ", numbers) + ".", "/fuel/purchase-orders");
        // Marked only when the notice went out (or there was nobody to tell), so a failure is retried tomorrow.
        automation.markOverdueAlerted(tenant, late.stream().map(FuelPurchaseOrder::getId).toList());
        return n;
    }

    private int notify(TenantId tenant, NotificationType type, String title, String message, String url) {
        List<Recipient> recipients = adminRecipients.resolveTenantAdmins(tenant);
        if (recipients.isEmpty()) return 0;
        notifications.send(NotificationRequest.builder().tenantId(tenant).type(type).title(title).message(message).actionUrl(url)
                .sourceModule("fuel").sourceEntityId(tenant.getValue().toString()).recipients(recipients).build());
        return 1;
    }
}

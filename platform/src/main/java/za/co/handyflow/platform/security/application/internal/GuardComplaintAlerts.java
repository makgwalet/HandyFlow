// security/application/internal/GuardComplaintAlerts.java
package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.security.domain.model.GuardComplaint;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

/**
 * Tells the tenant's administrators when an urgent complaint (critical severity, or excessive force, firearm,
 * theft or harassment) is logged. A failure to notify is logged and never stops the complaint being recorded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardComplaintAlerts {

    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    public void urgentComplaint(TenantId tenantId, GuardComplaint c, String guardName) {
        try {
            List<Recipient> admins = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            if (admins.isEmpty()) {
                log.warn("[Security] Urgent complaint {} logged but no admin recipients could be resolved", c.getComplaintNumber());
                return;
            }
            String category = c.getCategory().name().toLowerCase().replace('_', ' ');
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId)
                    .type(NotificationType.GUARD_COMPLAINT_URGENT)
                    .title("Urgent complaint " + c.getComplaintNumber() + ": " + category)
                    .message("A " + c.getSeverity().name().toLowerCase() + " " + category + " complaint was logged about "
                            + (guardName == null ? "a guard" : guardName) + ". It needs review.")
                    .actionUrl("/security/complaints/" + c.getId())
                    .sourceModule("security")
                    .recipients(admins)
                    .build());
        } catch (Exception e) {
            log.error("[Security] Could not send the urgent complaint alert for {}: {}", c.getComplaintNumber(), e.getMessage(), e);
        }
    }
}

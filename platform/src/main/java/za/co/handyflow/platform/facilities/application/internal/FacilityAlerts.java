package za.co.handyflow.platform.facilities.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

/**
 * Alerts to a tenant's administrators. An alert is a courtesy to the people doing the work: if the mail server or
 * notification store is down it is logged and dropped, never allowed to fail (and roll back) the business action
 * that raised it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FacilityAlerts {

    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    public void toAdmins(TenantId tenantId, NotificationType type, String title, String message,
                         String actionUrl, String sourceEntityId) {
        try {
            List<Recipient> recipients = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            if (recipients.isEmpty()) return;
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId)
                    .type(type)
                    .title(title)
                    .message(message)
                    .actionUrl(actionUrl)
                    .sourceModule("facilities")
                    .sourceEntityId(sourceEntityId)
                    .recipients(recipients)
                    .build());
        } catch (Exception e) {
            log.warn("[Facilities] Could not raise {} alert '{}' for tenant {}: {}", type, title, tenantId.getValue(), e.getMessage());
        }
    }
}

package za.co.handyflow.platform.facilitiesmanagement.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

/**
 * The one place facilities management raises in-app/email alerts to the tenant's administrators. An alert is a
 * courtesy, never part of the business transaction: if the notification service is down or throws, that is logged
 * and swallowed so a work order, invoice or credit note is never lost because an email could not be queued.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FmAlerts {

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
                    .sourceModule("facilitiesmanagement")
                    .sourceEntityId(sourceEntityId)
                    .recipients(recipients)
                    .build());
        } catch (Exception e) {
            log.warn("[FM] Could not raise {} alert '{}' for tenant {}: {}", type, title, tenantId.getValue(), e.getMessage());
        }
    }
}

package za.co.handyflow.platform.invoicing.application.internal;

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
 * Shared by every invoicing service that needs to alert STAFF (as opposed to
 * emailing an external customer directly — that stays a separate, direct
 * EmailService.sendWithAttachment() call, since the notification pipeline
 * has no attachment support).
 *
 * Every failure here is swallowed and logged — a notification problem must
 * never surface as a failure of the invoice/quote/schedule operation that
 * triggered it, same principle as TenantAdminRecipientsImpl's own isolation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaffNotifier {

    private final NotificationService notificationService;
    private final TenantAdminRecipients tenantAdminRecipients;

    public void notify(TenantId tenantId, NotificationType type, String title, String message,
                       String actionUrl, String sourceEntityId) {
        try {
            List<Recipient> staff = tenantAdminRecipients.resolveTenantAdmins(tenantId);
            if (staff.isEmpty()) {
                log.debug("No staff recipients for tenant={} — skipping {} notification", tenantId, type);
                return;
            }
            notificationService.send(NotificationRequest.builder()
                    .tenantId(tenantId)
                    .type(type)
                    .title(title)
                    .message(message)
                    .actionUrl(actionUrl)
                    .sourceModule("invoicing")
                    .sourceEntityId(sourceEntityId)
                    .recipients(staff)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to raise {} notification for tenant={}: {}", type, tenantId, e.getMessage());
        }
    }
}
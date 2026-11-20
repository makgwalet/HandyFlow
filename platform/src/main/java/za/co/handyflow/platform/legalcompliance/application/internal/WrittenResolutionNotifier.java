package za.co.handyflow.platform.legalcompliance.application.internal;

import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.TenantAdminRecipients;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Sends a written-resolution notice to the given user accounts. A seam so the flow can be tested without the notification and identity modules. */
@FunctionalInterface
public interface WrittenResolutionNotifier {

    void send(TenantId tenant, NotificationType type, String title, String message, Collection<UUID> userIds);

    @Component
    class ViaNotifications implements WrittenResolutionNotifier {
        private final NotificationService notifications;
        private final TenantAdminRecipients recipients;

        public ViaNotifications(NotificationService notifications, TenantAdminRecipients recipients) { this.notifications = notifications; this.recipients = recipients; }

        @Override public void send(TenantId tenant, NotificationType type, String title, String message, Collection<UUID> userIds) {
            if (userIds.isEmpty()) return;
            List<Recipient> to = recipients.resolveUsers(tenant, userIds);
            if (to.isEmpty()) return;
            notifications.send(NotificationRequest.builder().tenantId(tenant).type(type).title(title).message(message).actionUrl("/legalcompliance/written-resolutions").sourceModule("legalcompliance").recipients(to).build());
        }
    }
}

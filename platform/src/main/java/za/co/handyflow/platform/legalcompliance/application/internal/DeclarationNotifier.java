package za.co.handyflow.platform.legalcompliance.application.internal;

import org.springframework.stereotype.Component;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.application.internal.NotificationService;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;

/** Sends a declaration-campaign notice to the people given. A seam so campaigns can be tested without the notification module. */
@FunctionalInterface
public interface DeclarationNotifier {

    void send(TenantId tenant, NotificationType type, String title, String message, List<Recipient> to);

    @Component
    class ViaNotifications implements DeclarationNotifier {
        private final NotificationService notifications;

        public ViaNotifications(NotificationService notifications) { this.notifications = notifications; }

        @Override public void send(TenantId tenant, NotificationType type, String title, String message, List<Recipient> to) {
            notifications.send(NotificationRequest.builder().tenantId(tenant).type(type).title(title).message(message).actionUrl("/legalcompliance/declarations").sourceModule("legalcompliance").recipients(to).build());
        }
    }
}

package za.co.handyflow.platform.notifications.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.notifications.application.NotificationRequest;
import za.co.handyflow.platform.notifications.application.Recipient;
import za.co.handyflow.platform.notifications.domain.model.Notification;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.domain.repository.NotificationRepository;

/**
 * Single entry point every other module uses to raise a notification:
 *
 * <pre>{@code
 * notificationService.send(NotificationRequest.builder()
 *         .tenantId(tenantId)
 *         .type(NotificationType.ASSET_BREAKDOWN)
 *         .title("Machine breakdown: " + asset.getFleetNumber())
 *         .message(asset.getName() + " reported a breakdown at " + asset.getCurrentSite())
 *         .actionUrl("/earthmoving/assets/" + asset.getId())
 *         .sourceModule("earthmoving")
 *         .sourceEntityId(asset.getId().toString())
 *         .recipients(fleetManagers)
 *         .build());
 * }</pre>
 * <p>
 * HOW THIS WORKS (read this before changing it):
 * <ol>
 *   <li>IN_APP rows are written synchronously, right here, inside whatever
 *       transaction the caller is already in. This is what makes the
 *       notification atomic with the business action — it commits or rolls
 *       back together with it.</li>
 *   <li>An internal event is then published. Because it's picked up by an
 *       {@code AFTER_COMMIT} listener ({@link NotificationDispatchListener}),
 *       EMAIL and SMS are only ever sent once the transaction has actually
 *       committed — never for an action that got rolled back.</li>
 *   <li>This method itself returns immediately after the DB write; it does
 *       NOT wait for email/SMS delivery. Those happen off-thread, after
 *       commit, and their failures are isolated (see the channel senders) so
 *       a bad phone number can never fail the business operation that
 *       triggered the notification.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void send(NotificationRequest request) {
        if (request.channels().contains(NotificationChannel.IN_APP)) {
            for (Recipient recipient : request.recipients()) {
                if (!recipient.isPlatformUser()) {
                    continue; // no app inbox to write to for external recipients
                }
                Notification notification = Notification.create(
                        request.tenantId(),
                        recipient.userId(),
                        request.type(),
                        request.severity(),
                        request.title(),
                        request.message(),
                        request.actionUrl(),
                        request.sourceModule(),
                        request.sourceEntityId()
                );
                notificationRepository.save(notification);
            }
        }

        // Published now, delivered after commit — see NotificationDispatchListener.
        eventPublisher.publishEvent(new NotificationDispatchEvent(request));

        log.debug("Notification queued type={} recipients={} channels={}",
                request.type(), request.recipients().size(), request.channels());
    }
}
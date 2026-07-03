package za.co.handyflow.platform.notifications.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * NotificationResponse — adds {@code readAt} alongside the existing
 * {@code read} boolean.
 *
 * WHY ADD readAt WHEN isRead ALREADY EXISTS?
 * isRead answers "can I stop showing the unread dot" — readAt answers "when
 * did the recipient actually see this", which the UI needs for things like
 * "read 2 hours ago" and which compliance/audit views need for questions
 * like "did the on-call supervisor see the duress alert, and when". Cheap
 * to add now; expensive to reconstruct later since old rows would have no
 * timestamp to backfill.
 *
 * Update Notification.markRead() to set readAt = Instant.now() alongside
 * read = true, and update NotificationQueryService.toResponse() to pass
 * n.getReadAt() as the new constructor argument.
 */
public record NotificationResponse(
        UUID id,
        za.co.handyflow.platform.notifications.domain.model.NotificationType type,
        za.co.handyflow.platform.notifications.domain.model.NotificationSeverity severity,
        String title,
        String message,
        String actionUrl,
        String sourceModule,
        String sourceEntityId,
        boolean read,
        Instant readAt,
        Instant createdAt
) {}
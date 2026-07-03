package za.co.handyflow.platform.notifications.domain.model;

/**
 * Delivery channel for a notification.
 * <p>
 * IN_APP is a special case: it is always persisted regardless of user
 * preference, because it IS the notification centre — muting it would mean
 * the user has no record the event ever happened. EMAIL and SMS are the
 * only channels a user can opt out of via {@link NotificationPreference}.
 */
public enum NotificationChannel {
    IN_APP,
    EMAIL,
    SMS
}
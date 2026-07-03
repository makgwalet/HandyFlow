package za.co.handyflow.platform.notifications.application.internal;

import za.co.handyflow.platform.notifications.application.NotificationRequest;

/**
 * Internal event: "a notification was recorded, now go deliver it on the
 * non-in-app channels". This never leaves the JVM and is not part of any
 * public API — it exists purely so we can hook Spring's
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, which requires
 * an ApplicationEvent to listen for.
 */
record NotificationDispatchEvent(NotificationRequest request) {
}
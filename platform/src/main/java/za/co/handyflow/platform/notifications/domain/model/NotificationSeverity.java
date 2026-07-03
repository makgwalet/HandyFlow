package za.co.handyflow.platform.notifications.domain.model;

/**
 * Severity drives UI treatment (icon/colour on the notification bell) and
 * can later drive escalation rules (e.g. CRITICAL unread for > 1hr triggers
 * a second reminder). Kept deliberately small — three levels is enough for
 * any operator-facing product; more than that and nobody can tell them apart.
 */
public enum NotificationSeverity {
    INFO,
    WARNING,
    CRITICAL
}
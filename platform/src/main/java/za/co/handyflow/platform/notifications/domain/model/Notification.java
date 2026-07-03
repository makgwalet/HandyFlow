package za.co.handyflow.platform.notifications.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * A single in-app notification, scoped to exactly one recipient.
 * <p>
 * WHY one row per recipient (not one row shared by many users with a join
 * table for read-state)? Because "read" is inherently per-user — if five
 * fleet managers get notified a machine broke down, one of them marking it
 * read must not hide it from the other four. Duplicating the row is the
 * simplest model that gets this right, and at typical notification volumes
 * (thousands, not billions, per tenant) the storage cost is irrelevant next
 * to the correctness win.
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Notification {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(nullable = false)
    private String type;      // NotificationType#name()

    @Column(nullable = false)
    private String severity;  // NotificationSeverity#name()

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "action_url")
    private String actionUrl;

    @Column(name = "source_module", nullable = false)
    private String sourceModule;   // e.g. "earthmoving" — lets the UI group/filter/deep-link

    @Column(name = "source_entity_id")
    private String sourceEntityId; // e.g. the asset UUID as a string

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Notification create(TenantId tenantId, UUID recipientUserId,
                                      NotificationType type, NotificationSeverity severity,
                                      String title, String message, String actionUrl,
                                      String sourceModule, String sourceEntityId) {
        Notification n = new Notification();
        n.tenantId = tenantId;
        n.recipientUserId = recipientUserId;
        n.type = type.name();
        n.severity = severity.name();
        n.title = title;
        n.message = message;
        n.actionUrl = actionUrl;
        n.sourceModule = sourceModule;
        n.sourceEntityId = sourceEntityId;
        n.createdAt = Instant.now();
        return n;
    }

    public void markRead() {
        if (this.readAt == null) {
            this.readAt = Instant.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }
}
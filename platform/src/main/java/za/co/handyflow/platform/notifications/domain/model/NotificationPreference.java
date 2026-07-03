package za.co.handyflow.platform.notifications.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user, per-channel opt-out flag.
 * <p>
 * DESIGN CHOICE: absence of a row means "enabled". We do NOT insert a
 * default row for every user/channel/type combination up front — that's a
 * combinatorial explosion for zero benefit. A missing row is simply the
 * default state; a row only exists once a user has actively changed
 * something. This is the same pattern most SaaS notification-settings
 * pages use under the hood.
 * <p>
 * Scope is per-channel, not per-(channel + type). A per-type preference
 * matrix ("email me on breakdowns but not service-due") is a reasonable
 * future enhancement, but it's a UI and data-model expansion nobody has
 * asked for yet — building it now would be speculative complexity. Add a
 * `notification_type` column and adjust the unique constraint when there's
 * an actual product requirement for it.
 */
@Entity
@Table(name = "notification_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_notification_pref_user_channel",
                columnNames = {"tenant_id", "user_id", "channel"}))
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class NotificationPreference {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String channel; // NotificationChannel#name() — EMAIL or SMS only

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static NotificationPreference create(TenantId tenantId, UUID userId,
                                                NotificationChannel channel, boolean enabled) {
        NotificationPreference p = new NotificationPreference();
        p.tenantId = tenantId;
        p.userId = userId;
        p.channel = channel.name();
        p.enabled = enabled;
        p.updatedAt = Instant.now();
        return p;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
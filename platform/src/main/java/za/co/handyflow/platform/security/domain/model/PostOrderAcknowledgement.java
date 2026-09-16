package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * "That creates evidence." — per-guard, per-version acknowledgment,
 * with the offline/device/sync fields the product owner's own data
 * model specified exactly (deviceId, syncStatus, here as
 * deviceHardwareId/acknowledgedOffline/syncedAt). Deliberately
 * immutable once created — an acknowledgment is a historical fact, not
 * something to edit. version is denormalized on purpose: the PostOrder
 * row this points to could later be superseded or archived, but this
 * record must always show which exact version was actually read,
 * unaffected by the source row's own later status changes.
 */
@Entity
@Table(name = "security_post_order_acknowledgements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostOrderAcknowledgement {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "post_order_id", nullable = false) private UUID postOrderId;
    @Column(nullable = false) private int version;
    @Column(name = "guard_id", nullable = false) private UUID guardId;
    @Column(name = "acknowledged_at", nullable = false) private Instant acknowledgedAt;
    @Column(name = "device_hardware_id") private String deviceHardwareId;
    @Column(name = "acknowledged_offline", nullable = false) private boolean acknowledgedOffline;
    @Column(name = "synced_at") private Instant syncedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static PostOrderAcknowledgement create(TenantId tenantId, UUID postOrderId, int version,
                                                   UUID guardId, Instant acknowledgedAt,
                                                   String deviceHardwareId, boolean acknowledgedOffline) {
        PostOrderAcknowledgement a = new PostOrderAcknowledgement();
        a.tenantId = tenantId;
        a.postOrderId = postOrderId;
        a.version = version;
        a.guardId = guardId;
        a.acknowledgedAt = acknowledgedAt;
        a.deviceHardwareId = deviceHardwareId;
        a.acknowledgedOffline = acknowledgedOffline;
        // FIX: an online acknowledgment is synced the instant it's
        // created (there was nothing to queue); an offline one starts
        // unsynced — syncedAt is set later, once the sync queue actually
        // delivers it, matching this session's own Agriculture offline-
        // sync precedent for what "synced" genuinely means.
        a.syncedAt = acknowledgedOffline ? null : Instant.now();
        a.createdAt = Instant.now();
        return a;
    }

    public void markSynced() {
        this.syncedAt = Instant.now();
    }
}

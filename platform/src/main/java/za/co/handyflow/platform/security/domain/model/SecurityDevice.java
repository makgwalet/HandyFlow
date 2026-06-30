// security/domain/model/SecurityDevice.java

package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * SecurityDevice — a physical device (phone/tablet) used for guard operations.
 *
 * Two types (Part 6.4):
 *
 *   SHARED_SITE_DEVICE — one device at a guardhouse, used by rotating guards.
 *     Multiple guards share it sequentially; each guard opens a DeviceSession
 *     at shift start and closes it at clock-out.
 *     kiosk_mode_enabled = true (lock screen between sessions).
 *
 *   PERSONAL_GUARD_DEVICE — one phone permanently assigned to one guard.
 *     Used in Enterprise tier.  device_hardware_id is bound to registered_device_id
 *     on the Guard record.
 *     kiosk_mode_enabled = false (guard keeps normal app access).
 *
 * WHY track battery_pct and last_seen_at?
 * A device that hasn't been seen for >2h during a shift, or whose battery is
 * critically low, should alert the supervisor before the device dies mid-patrol.
 * These are updated by the Shield app on every API call (HTTP header or heartbeat).
 */
@Entity
@Table(name = "security_devices")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class SecurityDevice {

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value",
            column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "device_hardware_id", nullable = false, length = 200)
    private String deviceHardwareId;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false, length = 30)
    private DeviceType deviceType;

    @Column(name = "kiosk_mode_enabled", nullable = false)
    private boolean kioskModeEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceStatus status = DeviceStatus.ACTIVE;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "battery_pct")
    private Integer batteryPct;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Factory ────────────────────────────────────────────────────────────────

    public static SecurityDevice create(TenantId tenantId, UUID siteId,
                                        String deviceHardwareId, String deviceName,
                                        DeviceType deviceType) {
        SecurityDevice d  = new SecurityDevice();
        d.tenantId        = tenantId;
        d.siteId          = siteId;
        d.deviceHardwareId = deviceHardwareId;
        d.deviceName      = deviceName;
        d.deviceType      = deviceType;
        d.kioskModeEnabled = deviceType == DeviceType.SHARED_SITE_DEVICE;
        d.status          = DeviceStatus.ACTIVE;
        d.createdAt       = Instant.now();
        d.updatedAt       = Instant.now();
        return d;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void heartbeat(Integer batteryPct) {
        this.lastSeenAt = Instant.now();
        this.batteryPct = batteryPct;
        this.updatedAt  = Instant.now();
    }

    public void markLost() {
        this.status    = DeviceStatus.LOST;
        this.updatedAt = Instant.now();
    }

    public void decommission() {
        this.status    = DeviceStatus.DECOMMISSIONED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() { return status == DeviceStatus.ACTIVE; }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum DeviceType {
        SHARED_SITE_DEVICE,
        PERSONAL_GUARD_DEVICE
    }

    public enum DeviceStatus {
        ACTIVE, LOST, DECOMMISSIONED
    }
}
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

    // FIX (guard device lockdown, product owner's own explicit design):
    // nullable — only set for PERSONAL_GUARD_DEVICE rows, giving genuine
    // per-guard device HISTORY (multiple rows over time, each with its
    // own status) rather than the single overwritable
    // Guard.registeredDeviceId string this used to be the only record
    // of. That field is left in place (still the fast lookup used at
    // login) but this table is now the source of truth for "which
    // device was this guard using when."
    @Column(name = "guard_id")
    private UUID guardId;

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

    /**
     * FIX (guard device lockdown): the enrollment/replacement path for a
     * PERSONAL_GUARD_DEVICE — starts PENDING, not ACTIVE, matching the
     * product owner's own device-state list. GuardAuthService.enroll()
     * activates it immediately in the same flow (enrollment is already a
     * supervisor-witnessed action, so there's no separate confirmation
     * step needed there); the activation-code replacement flow is where
     * PENDING genuinely holds — a code has been issued but not yet
     * redeemed.
     */
    public static SecurityDevice createPendingForGuard(TenantId tenantId, UUID guardId,
                                                        String deviceHardwareId, String deviceName) {
        SecurityDevice d   = new SecurityDevice();
        d.tenantId         = tenantId;
        d.guardId          = guardId;
        d.deviceHardwareId = deviceHardwareId;
        d.deviceName       = deviceName;
        d.deviceType       = DeviceType.PERSONAL_GUARD_DEVICE;
        d.kioskModeEnabled = false;
        d.status           = DeviceStatus.PENDING;
        d.createdAt        = Instant.now();
        d.updatedAt        = Instant.now();
        return d;
    }

    // ── Mutations ──────────────────────────────────────────────────────────────

    public void heartbeat(Integer batteryPct) {
        this.lastSeenAt = Instant.now();
        this.batteryPct = batteryPct;
        this.updatedAt  = Instant.now();
    }

    public void activate() {
        this.status    = DeviceStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void markLost() {
        this.status    = DeviceStatus.LOST;
        this.updatedAt = Instant.now();
    }

    /**
     * FIX (guard device lockdown): the state a device moves to when a
     * replacement is issued and redeemed — distinct from a plain revoke,
     * so the history reads correctly ("this device was REPLACED on
     * <date>", not just "revoked for no stated reason").
     */
    public void markReplaced() {
        this.status    = DeviceStatus.REPLACED;
        this.updatedAt = Instant.now();
    }

    /**
     * A supervisor pulling a device from service directly — lost phone
     * reported by someone other than the guard, suspected compromise,
     * employment ending, etc. Distinct from markReplaced(): this one has
     * no new device on the other end of it yet.
     */
    public void revoke() {
        this.status    = DeviceStatus.REVOKED;
        this.updatedAt = Instant.now();
    }

    public void block() {
        this.status    = DeviceStatus.BLOCKED;
        this.updatedAt = Instant.now();
    }

    public void decommission() {
        this.status    = DeviceStatus.DECOMMISSIONED;
        this.updatedAt = Instant.now();
    }

    public boolean isActive() { return status == DeviceStatus.ACTIVE; }

    /**
     * FIX (guard device lockdown): what GuardAuthService.login() now
     * checks before letting a device authenticate — REVOKED, BLOCKED,
     * LOST, REPLACED, and DECOMMISSIONED are all "this device must not
     * authenticate," for different underlying reasons, deliberately
     * covered by a single check rather than enumerating them separately
     * at every call site.
     */
    public boolean canAuthenticate() { return status == DeviceStatus.ACTIVE; }

    // ── Enums ──────────────────────────────────────────────────────────────────

    public enum DeviceType {
        SHARED_SITE_DEVICE,
        PERSONAL_GUARD_DEVICE
    }

    public enum DeviceStatus {
        PENDING, ACTIVE, REVOKED, LOST, BLOCKED, REPLACED, DECOMMISSIONED
    }
}
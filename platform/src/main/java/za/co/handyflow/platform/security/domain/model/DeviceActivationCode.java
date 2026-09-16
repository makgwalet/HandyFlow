package za.co.handyflow.platform.security.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.TenantId;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * The supervisor-authorized device-replacement workflow, per the product
 * owner's own explicit design: "Guard loses phone. Guard contacts
 * supervisor. Supervisor initiates: Replace Device. System generates:
 * 6-digit / QR activation code... Backend verifies: Guard + activation
 * code + expiry + existing device status. Then: Old device -> REVOKED,
 * New device -> ACTIVE." This entity is that code — single-use, short-
 * lived, tied to one specific guard.
 */
@Entity
@Table(name = "guard_device_activation_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceActivationCode {

    private static final Duration VALIDITY = Duration.ofMinutes(15);
    private static final SecureRandom RNG = new SecureRandom();

    @Id
    private UUID id = UUID.randomUUID();

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "tenant_id", nullable = false))
    private TenantId tenantId;

    @Column(name = "guard_id", nullable = false) private UUID guardId;
    @Column(nullable = false, length = 10) private String code;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;
    @Column(name = "used_by_device_hardware_id") private String usedByDeviceHardwareId;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    public static DeviceActivationCode issue(TenantId tenantId, UUID guardId, UUID createdBy) {
        DeviceActivationCode c = new DeviceActivationCode();
        c.tenantId  = tenantId;
        c.guardId   = guardId;
        c.code      = generateCode();
        c.expiresAt = Instant.now().plus(VALIDITY);
        c.createdBy = createdBy;
        c.createdAt = Instant.now();
        return c;
    }

    // 6-digit numeric, matching the product owner's own spec exactly —
    // easy to read aloud over a phone call, which is the realistic
    // scenario this is for ("guard contacts supervisor").
    private static String generateCode() {
        return String.format("%06d", RNG.nextInt(1_000_000));
    }

    public boolean isValid() {
        return usedAt == null && Instant.now().isBefore(expiresAt);
    }

    public void redeem(String byDeviceHardwareId) {
        if (!isValid()) {
            throw new IllegalStateException(
                    usedAt != null ? "This code has already been used" : "This code has expired");
        }
        this.usedAt = Instant.now();
        this.usedByDeviceHardwareId = byDeviceHardwareId;
    }
}

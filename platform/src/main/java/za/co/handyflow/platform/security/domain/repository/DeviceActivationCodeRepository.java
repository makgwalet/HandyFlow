package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.DeviceActivationCode;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface DeviceActivationCodeRepository extends JpaRepository<DeviceActivationCode, UUID> {

    // Lookup during activation is by code alone — the guard's own app
    // doesn't have a guardId to hand at that point, only the code they
    // were given (over the phone or by QR) by their supervisor.
    @Query("SELECT c FROM DeviceActivationCode c WHERE c.tenantId = :tenantId AND c.code = :code AND c.usedAt IS NULL")
    Optional<DeviceActivationCode> findUnusedByCode(TenantId tenantId, String code);

    // FIX: the guard redeeming a replacement code has just lost access
    // to their device and has no session/JWT at all — genuinely no
    // tenant context to scope this lookup by, unlike every other query
    // in this codebase. The code itself (6-digit, single-use, 15-minute
    // expiry) is the only scoping mechanism available at this point;
    // everything downstream in GuardAuthService.activateDeviceReplacement()
    // uses the found row's own tenantId, never one trusted from the caller.
    @Query("SELECT c FROM DeviceActivationCode c WHERE c.code = :code AND c.usedAt IS NULL")
    Optional<DeviceActivationCode> findUnusedByCodeAnyTenant(String code);
}

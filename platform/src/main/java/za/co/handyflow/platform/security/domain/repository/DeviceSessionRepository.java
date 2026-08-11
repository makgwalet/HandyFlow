// security/domain/repository/DeviceSessionRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.DeviceSession;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, UUID> {

    /** The open session on a device, if any — enforces one-session-per-device. */
    @Query("""
        SELECT s FROM DeviceSession s
        WHERE s.deviceId = :deviceId
        AND s.endedAt IS NULL
        """)
    Optional<DeviceSession> findOpenByDevice(UUID deviceId);

    /** The open session for a guard across all devices — enforces one-session-per-guard. */
    @Query("""
        SELECT s FROM DeviceSession s
        WHERE s.guardId = :guardId
        AND s.endedAt IS NULL
        """)
    Optional<DeviceSession> findOpenByGuard(UUID guardId);

    @Query("""
        SELECT s FROM DeviceSession s
        WHERE s.shiftId = :shiftId
        """)
    Optional<DeviceSession> findByShiftId(UUID shiftId);

    /**
     * Tenant-wide paginated list, newest-first — backs GET /sessions, added
     * to close the audit gap: DeviceSessionsTab.tsx has been calling this
     * exact endpoint (?size=100) since it was written, but nothing on
     * DeviceSessionController ever implemented it, so the "Active"/"Recent"
     * views in that tab have always silently rendered empty.
     */
    @Query("""
        SELECT s FROM DeviceSession s
        WHERE s.tenantId = :tenantId
        ORDER BY s.startedAt DESC
        """)
    Page<DeviceSession> findByTenant(TenantId tenantId, Pageable pageable);
}
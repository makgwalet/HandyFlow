// security/domain/repository/DispatchRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Dispatch;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatchRepository extends JpaRepository<Dispatch, UUID> {

    @Query("""
        SELECT d FROM Dispatch d
        WHERE d.tenantId = :tenantId
        AND d.id = :id
        """)
    Optional<Dispatch> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT d FROM Dispatch d
        WHERE d.alarmEventId = :alarmEventId
        ORDER BY d.dispatchedAt DESC
        """)
    List<Dispatch> findByAlarmEvent(UUID alarmEventId);

    /** Currently open (unresolved) dispatches — the control room's "in progress" view. */
    @Query("""
        SELECT d FROM Dispatch d
        WHERE d.tenantId = :tenantId
        AND d.resolvedAt IS NULL
        ORDER BY d.dispatchedAt
        """)
    List<Dispatch> findOpen(TenantId tenantId);

    @Query("""
        SELECT d FROM Dispatch d
        WHERE d.tenantId = :tenantId
        AND d.dispatchedGuardId = :guardId
        ORDER BY d.dispatchedAt DESC
        """)
    List<Dispatch> findByGuard(TenantId tenantId, UUID guardId);
}

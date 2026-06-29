// security/domain/repository/ShiftSwapRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.ShiftSwapRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftSwapRepository extends JpaRepository<ShiftSwapRequest, UUID> {

    @Query("""
        SELECT r FROM ShiftSwapRequest r
        WHERE r.tenantId = :tenantId
        AND r.id = :id
        """)
    Optional<ShiftSwapRequest> findByTenantAndId(TenantId tenantId, UUID id);

    /** Open requests awaiting supervisor action. */
    @Query("""
        SELECT r FROM ShiftSwapRequest r
        WHERE r.tenantId = :tenantId
        AND r.status IN ('PENDING', 'PROPOSED_ACCEPTED')
        ORDER BY r.requestedAt
        """)
    Page<ShiftSwapRequest> findPending(TenantId tenantId, Pageable pageable);

    /** All requests made by or directed at a specific guard. */
    @Query("""
        SELECT r FROM ShiftSwapRequest r
        WHERE r.tenantId = :tenantId
        AND (r.requestingGuardId = :guardId OR r.proposedGuardId = :guardId)
        ORDER BY r.requestedAt DESC
        """)
    Page<ShiftSwapRequest> findByGuard(TenantId tenantId, UUID guardId, Pageable pageable);

    /** Any open swap request for a specific shift — prevents duplicate requests. */
    @Query("""
        SELECT r FROM ShiftSwapRequest r
        WHERE r.originalShiftId = :shiftId
        AND r.status IN ('PENDING', 'PROPOSED_ACCEPTED')
        """)
    List<ShiftSwapRequest> findOpenByShift(UUID shiftId);
}

// security/domain/repository/GuardRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.Guard;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface GuardRepository extends JpaRepository<Guard, UUID> {

    /**
     * All non-deleted guards for the tenant, ordered by name.
     * WHY include status-filtered guards (SUSPENDED, TERMINATED) in the
     * full list?  Management needs to see all guards, not just schedulable
     * ones.  The UI filters by status using the status filter pills.
     * Scheduling endpoints apply isSchedulable() separately.
     */
    @Query("""
        SELECT g FROM Guard g
        WHERE g.tenantId = :tenantId
        AND g.deletedAt IS NULL
        ORDER BY g.lastName, g.firstName
        """)
    Page<Guard> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT g FROM Guard g
        WHERE g.tenantId = :tenantId
        AND g.id = :id
        AND g.deletedAt IS NULL
        """)
    Optional<Guard> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT g FROM Guard g
        WHERE g.tenantId = :tenantId
        AND g.deletedAt IS NULL
        AND (LOWER(g.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
          OR LOWER(g.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
          OR LOWER(g.psiraNumber) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY g.lastName, g.firstName
        """)
    Page<Guard> searchActive(TenantId tenantId, String search, Pageable pageable);

    /**
     * PSiRA duplicate check on CREATE — any guard in this tenant with this
     * PSiRA number (not deleted).
     */
    boolean existsByTenantIdAndPsiraNumberAndDeletedAtIsNull(TenantId tenantId, String psiraNumber);

    /**
     * PSiRA duplicate check on UPDATE — any OTHER guard in this tenant with
     * this PSiRA number.
     *
     * WHY exclude the guard being updated?
     * Without this exclusion, updating any field on a guard who already has a
     * PSiRA number would fail the duplicate check against themselves.
     * The fix is to exclude the current guard's ID from the check.
     *
     * This is bug #3 from the production plan — the original code only checked
     * on create, allowing a guard to be edited into a duplicate PSiRA number.
     */
    @Query("""
        SELECT COUNT(g) > 0 FROM Guard g
        WHERE g.tenantId = :tenantId
        AND g.psiraNumber = :psiraNumber
        AND g.deletedAt IS NULL
        AND g.id <> :excludeId
        """)
    boolean existsByPsiraExcluding(TenantId tenantId, String psiraNumber, UUID excludeId);

    /**
     * Guards in ACTIVE status only — used by ShiftService when selecting
     * available guards for scheduling.
     */
    @Query("""
        SELECT g FROM Guard g
        WHERE g.tenantId = :tenantId
        AND g.deletedAt IS NULL
        AND g.status = 'ACTIVE'
        ORDER BY g.lastName, g.firstName
        """)
    Page<Guard> findSchedulable(TenantId tenantId, Pageable pageable);
}

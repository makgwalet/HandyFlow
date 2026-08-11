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

    boolean existsByTenantIdAndPsiraNumberAndDeletedAtIsNull(TenantId tenantId, String psiraNumber);

    @Query("""
        SELECT COUNT(g) > 0 FROM Guard g
        WHERE g.tenantId = :tenantId
        AND g.psiraNumber = :psiraNumber
        AND g.deletedAt IS NULL
        AND g.id <> :excludeId
        """)
    boolean existsByPsiraExcluding(TenantId tenantId, String psiraNumber, UUID excludeId);

    @Query("""
        SELECT g FROM Guard g
        WHERE g.tenantId = :tenantId
        AND g.deletedAt IS NULL
        AND g.status = 'ACTIVE'
        ORDER BY g.lastName, g.firstName
        """)
    Page<Guard> findSchedulable(TenantId tenantId, Pageable pageable);

    // ── Guard authentication (Phase 1.5) ──────────────────────────────────────

    /**
     * Look up a guard by phone number across all tenants — used by the
     * guard login endpoint where the guard identifies by phone, not email.
     * See class-level usage in GuardAuthService for the full rationale
     * (no tenant filter needed since phone is globally unique, and the PIN
     * check afterward is the actual authentication step).
     */
    @Query("""
        SELECT g FROM Guard g
        WHERE g.phone = :phone
        AND g.deletedAt IS NULL
        AND g.active = true
        """)
    Optional<Guard> findActiveByPhone(String phone);

    /**
     * Look up a guard by employee code across all tenants (V214) — same
     * chicken-and-egg rationale as findActiveByPhone: the guard app doesn't
     * know the tenant yet at the point of login, so employee codes are
     * generated to be globally unique (GuardService.generateEmployeeCode)
     * specifically so this lookup can work the same way phone lookup does.
     */
    @Query("""
        SELECT g FROM Guard g
        WHERE g.employeeCode = :employeeCode
        AND g.deletedAt IS NULL
        AND g.active = true
        """)
    Optional<Guard> findActiveByEmployeeCode(String employeeCode);

    /**
     * Global existence check used during employee-code generation to detect
     * (rare) collisions between two tenants' independently-derived prefixes.
     * Deliberately NOT scoped to active/non-deleted -- a code must never be
     * reissued even to a different tenant once it's ever been assigned.
     */
    boolean existsByEmployeeCode(String employeeCode);

    /**
     * Branch-scoped list — ready for the future enforcement layer (not yet
     * wired into any controller; see BranchController's ENFORCEMENT NOTE
     * for what's still missing). Guard.primaryBranchId already existed
     * before this addition, unlike Site.branchId which needed a V218
     * migration first.
     */
    @Query("""
        SELECT g FROM Guard g
        WHERE g.tenantId = :tenantId
        AND g.primaryBranchId = :branchId
        AND g.deletedAt IS NULL
        ORDER BY g.lastName, g.firstName
        """)
    Page<Guard> findAllActiveByBranch(TenantId tenantId, UUID branchId, Pageable pageable);

    /**
     * Find guard by ID for authentication purposes — includes all statuses
     * so GuardAuthService can return appropriate error messages
     * (e.g. "your account is suspended" rather than "guard not found").
     */
    @Query("""
        SELECT g FROM Guard g
        WHERE g.id = :id
        AND g.deletedAt IS NULL
        """)
    Optional<Guard> findByIdForAuth(UUID id);
}
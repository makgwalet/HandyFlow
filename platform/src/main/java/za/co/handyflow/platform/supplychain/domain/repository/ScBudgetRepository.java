package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.enums.BudgetScopeType;
import za.co.handyflow.platform.supplychain.domain.model.ScBudget;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScBudgetRepository extends JpaRepository<ScBudget, UUID> {

    @Query("SELECT b FROM ScBudget b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<ScBudget> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT b FROM ScBudget b WHERE b.tenantId = :tenantId ORDER BY b.periodStart DESC, b.name")
    List<ScBudget> findAllForTenant(@Param("tenantId") UUID tenantId);

    @Query("SELECT b FROM ScBudget b WHERE b.tenantId = :tenantId AND b.status = 'ACTIVE' AND b.periodStart <= :date AND b.periodEnd >= :date")
    List<ScBudget> findActiveCovering(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    /**
     * Same as findActiveCovering but takes a row lock, so two concurrent PO approvals against one
     * hard-stop budget are serialised and the second sees the first's commitment.
     * Ordered by id so concurrent callers lock in the same order (no deadlock).
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ScBudget b WHERE b.tenantId = :tenantId AND b.status = 'ACTIVE' AND b.periodStart <= :date AND b.periodEnd >= :date ORDER BY b.id")
    List<ScBudget> lockActiveCovering(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);

    /** Row lock on one budget, used to serialise allocation changes and ancestor checks under a shared parent. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM ScBudget b WHERE b.tenantId = :tenantId AND b.id = :id")
    Optional<ScBudget> lockByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Active budgets for the same scope whose period overlaps [start, end] — a second one would double-count. */
    @Query("""
            SELECT b FROM ScBudget b
            WHERE b.tenantId = :tenantId AND b.status = 'ACTIVE' AND b.scopeType = :scopeType
              AND LOWER(b.scopeValue) = LOWER(:scopeValue)
              AND b.periodStart <= :end AND b.periodEnd >= :start
            """)
    List<ScBudget> findOverlapping(@Param("tenantId") UUID tenantId, @Param("scopeType") BudgetScopeType scopeType,
                                   @Param("scopeValue") String scopeValue, @Param("start") LocalDate start,
                                   @Param("end") LocalDate end);
}

// fuel/domain/repository/FuelDispatchRepository.java

package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelDispatch;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelDispatchRepository extends JpaRepository<FuelDispatch, UUID> {

    @Query("SELECT d FROM FuelDispatch d WHERE d.tankId = :tankId AND d.deletedAt IS NULL ORDER BY d.dispatchedAt DESC")
    Page<FuelDispatch> findByTank(UUID tankId, Pageable pageable);

    @Query("SELECT d FROM FuelDispatch d WHERE d.tenantId = :tenantId AND d.deletedAt IS NULL ORDER BY d.dispatchedAt DESC")
    Page<FuelDispatch> findAllActive(TenantId tenantId, Pageable pageable);

    /** Backs the monthly usage report — full (non-paginated) dispatches within a date range. */
    @Query("SELECT d FROM FuelDispatch d WHERE d.tenantId = :tenantId AND d.deletedAt IS NULL AND d.dispatchedAt BETWEEN :from AND :to ORDER BY d.dispatchedAt ASC")
    List<FuelDispatch> findByTenantAndDispatchedAtBetween(TenantId tenantId, Instant from, Instant to);

    /**
     * Batch per-tank litres-dispensed sum over a window — backs the
     * capacity/utilization forecast ("days until empty at current usage").
     * Batched across every tank in one query rather than one query per tank,
     * same N+1 avoidance as every other batch query in this codebase.
     */
    @Query("""
        SELECT d.tankId, COALESCE(SUM(d.litresDispensed), 0)
        FROM FuelDispatch d
        WHERE d.tankId IN :tankIds AND d.deletedAt IS NULL AND d.dispatchedAt BETWEEN :from AND :to
        GROUP BY d.tankId
        """)
    List<Object[]> sumLitresDispensedByTankIdsRaw(List<UUID> tankIds, Instant from, Instant to);

    default java.util.Map<UUID, java.math.BigDecimal> sumLitresDispensedByTankIds(List<UUID> tankIds, Instant from, Instant to) {
        if (tankIds == null || tankIds.isEmpty()) return java.util.Map.of();
        return sumLitresDispensedByTankIdsRaw(tankIds, from, to).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (java.math.BigDecimal) row[1]));
    }

    // ADR-001 W4 (FuelFacade): the tenant's OWN fuel only, that is dispatches to its own vehicles or assets. A dispatch to a customer is a sale
    // of fuel, not the tenant's own consumption, and must never be offered for allocation to production.
    @Query("""
        SELECT d FROM FuelDispatch d
        WHERE d.tenantId = :tenantId AND d.deletedAt IS NULL AND d.customerId IS NULL
        AND (d.vehicleId IS NOT NULL OR d.assetId IS NOT NULL)
        AND d.dispatchedAt >= :from AND d.dispatchedAt < :to
        ORDER BY d.dispatchedAt DESC
        """)
    List<FuelDispatch> findOwnBetween(TenantId tenantId, Instant from, Instant to, Pageable pageable);

    @Query("""
        SELECT d FROM FuelDispatch d
        WHERE d.tenantId = :tenantId AND d.id = :id AND d.deletedAt IS NULL AND d.customerId IS NULL
        AND (d.vehicleId IS NOT NULL OR d.assetId IS NOT NULL)
        """)
    Optional<FuelDispatch> findOwnById(TenantId tenantId, UUID id);
}

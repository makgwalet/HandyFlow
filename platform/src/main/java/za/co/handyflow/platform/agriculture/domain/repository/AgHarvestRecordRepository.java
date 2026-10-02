package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgHarvestRecord;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

/** Append-only, cycle-scoped — mirrors AgFeedRecordRepository's own shape. */
public interface AgHarvestRecordRepository extends JpaRepository<AgHarvestRecord, UUID> {

    @Query("SELECT h FROM AgHarvestRecord h WHERE h.tenantId = :tenantId AND h.id = :id")
    Optional<AgHarvestRecord> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT h FROM AgHarvestRecord h WHERE h.tenantId = :tenantId AND h.cropCycleId = :cropCycleId ORDER BY h.harvestDate DESC")
    Page<AgHarvestRecord> findByCropCycle(TenantId tenantId, UUID cropCycleId, Pageable pageable);

    // Backs AgCostReportingService's yield-per-hectare figure. Summed as
    // recorded — assumes a cycle's harvest records share one unit of
    // measure (the crop type's own default), which is the practical case;
    // a cycle harvested in mixed units would need this revisited.
    @Query("SELECT COALESCE(SUM(h.quantityHarvested), 0) FROM AgHarvestRecord h WHERE h.tenantId = :tenantId AND h.cropCycleId = :cropCycleId")
    BigDecimal sumQuantityByCropCycle(TenantId tenantId, UUID cropCycleId);

    // Quantity per unit, so yield can be converted to the crop's own unit before summing. Rows are [unitOfMeasure, quantity].
    @Query("SELECT h.unitOfMeasure, SUM(h.quantityHarvested) FROM AgHarvestRecord h WHERE h.tenantId = :tenantId AND h.cropCycleId = :cropCycleId GROUP BY h.unitOfMeasure")
    List<Object[]> sumQuantityByCropCycleAndUnit(TenantId tenantId, UUID cropCycleId);

    // Trends: [harvestDate, quantityHarvested, unitOfMeasure, cropCycleId].
    @Query("SELECT h.harvestDate, h.quantityHarvested, h.unitOfMeasure, h.cropCycleId FROM AgHarvestRecord h WHERE h.tenantId = :tenantId AND h.harvestDate >= :startDate AND h.harvestDate <= :endDate")
    List<Object[]> findBetween(TenantId tenantId, LocalDate startDate, LocalDate endDate);

    // Harvests with labour hours that have not been costed yet, newest first (ADR-001 W3). See AgInputApplicationRepository for the rules.
    @Query("""
        SELECT h FROM AgHarvestRecord h
        WHERE h.tenantId = :tenantId AND h.laborHours > 0
        AND h.cropCycleId IN (SELECT c.id FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.farmId = :farmId AND c.deletedAt IS NULL)
        AND NOT EXISTS (SELECT 1 FROM AgCostEntry e WHERE e.tenantId = :tenantId AND e.sourceType = 'HR_LABOUR' AND e.sourceRef = h.id AND e.status = 'ACTIVE')
        ORDER BY h.harvestDate DESC, h.createdAt DESC
        """)
    List<AgHarvestRecord> findUncostedLabourForFarm(TenantId tenantId, UUID farmId, Pageable pageable);
}

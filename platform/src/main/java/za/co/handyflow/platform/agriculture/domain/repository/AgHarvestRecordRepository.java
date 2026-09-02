package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgHarvestRecord;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.util.Optional;
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
}

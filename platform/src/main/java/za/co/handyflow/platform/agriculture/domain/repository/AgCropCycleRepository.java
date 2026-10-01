package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgCropCycle;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Farm-scoped, mirroring AgAnimalRepository/AgGroupRepository's own shape —
 * AgCropCycle plays the same "central tracking unit" role for Crops that
 * AgGroup plays for Livestock. Ordered by createdAt DESC rather than a
 * name/tag field, since cycleName/variety are both optional and there is
 * no natural alphabetic sort key on this entity.
 */
public interface AgCropCycleRepository extends JpaRepository<AgCropCycle, UUID> {

    @Query("SELECT c FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL")
    Optional<AgCropCycle> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT c FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.farmId = :farmId AND c.deletedAt IS NULL ORDER BY c.createdAt DESC")
    Page<AgCropCycle> findAllActiveForFarm(TenantId tenantId, UUID farmId, Pageable pageable);

    @Query("SELECT c FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.farmId = :farmId AND c.status = :status AND c.deletedAt IS NULL ORDER BY c.createdAt DESC")
    Page<AgCropCycle> findByStatusForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable);

    // FIX (Agriculture GAP 4 — mobile gap report, "Home/Today" summary).
    // "Active" here means still in the field — excludes the three
    // terminal statuses (HARVESTED, FAILED, ABANDONED), matching this
    // entity's own documented status list.
    @Query("SELECT COUNT(c) FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.farmId = :farmId AND c.deletedAt IS NULL AND c.status NOT IN ('HARVESTED', 'FAILED', 'ABANDONED')")
    long countActiveForFarm(TenantId tenantId, UUID farmId);

    @Query("SELECT c FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.seasonId = :seasonId AND c.deletedAt IS NULL ORDER BY c.createdAt DESC")
    Page<AgCropCycle> findAllActiveForSeason(TenantId tenantId, UUID seasonId, Pageable pageable);

    // Tenant-wide dashboard aggregates. One grouped query each instead of one query per farm.
    // Rows are [farmId, status, count, hectares].
    @Query("SELECT c.farmId, c.status, COUNT(c), SUM(c.areaPlantedHectares) FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL GROUP BY c.farmId, c.status")
    List<Object[]> summarizeByFarmAndStatus(TenantId tenantId);

    // Rows are [farmId, cropTypeId, count, hectares] for cycles that are in the ground (PLANTED, GROWING, HARVESTING).
    @Query("SELECT c.farmId, c.cropTypeId, COUNT(c), SUM(c.areaPlantedHectares) FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL AND c.status IN ('PLANTED', 'GROWING', 'HARVESTING') GROUP BY c.farmId, c.cropTypeId")
    List<Object[]> summarizeInProductionByFarmAndCrop(TenantId tenantId);

    // Crops still in the field whose expected harvest date is on or before :until (overdue and upcoming harvests).
    @Query("SELECT c FROM AgCropCycle c WHERE c.tenantId = :tenantId AND c.farmId = :farmId AND c.deletedAt IS NULL AND c.status IN ('PLANTED', 'GROWING') AND c.expectedHarvestDate IS NOT NULL AND c.expectedHarvestDate <= :until ORDER BY c.expectedHarvestDate ASC")
    List<AgCropCycle> findHarvestDueForFarm(TenantId tenantId, UUID farmId, LocalDate until);
}

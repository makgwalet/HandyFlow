package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

public interface AgAnimalRepository extends JpaRepository<AgAnimal, UUID> {

    @Query("SELECT a FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.id = :id AND a.deletedAt IS NULL")
    Optional<AgAnimal> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT a FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.deletedAt IS NULL ORDER BY a.tagNumber")
    Page<AgAnimal> findAllActiveForFarm(TenantId tenantId, UUID farmId, Pageable pageable);

    // FIX (Agriculture GAP 4 — mobile gap report, "Home/Today" summary):
    // backs the animal-count tile — avoids pulling a full page just to
    // count.
    @Query("SELECT COUNT(a) FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.deletedAt IS NULL AND a.status = 'ACTIVE'")
    long countActiveForFarm(TenantId tenantId, UUID farmId);

    @Query("SELECT a FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.status = :status AND a.deletedAt IS NULL ORDER BY a.tagNumber")
    Page<AgAnimal> findByStatusForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable);

    @Query("SELECT a FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.productionAreaId = :productionAreaId AND a.deletedAt IS NULL ORDER BY a.tagNumber")
    Page<AgAnimal> findAllActiveForProductionArea(TenantId tenantId, UUID productionAreaId, Pageable pageable);

    // Backs the pre-insert uniqueness check in AgAnimalService.createAnimal() —
    // mirrors EarthAssetRepository.existsActiveByFleetNumber(), fronting the
    // DB-level uq_ag_animals_tenant_farm_tag unique index.
    @Query("SELECT COUNT(a) > 0 FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.tagNumber = :tagNumber AND a.deletedAt IS NULL")
    boolean existsActiveByFarmAndTagNumber(TenantId tenantId, UUID farmId, String tagNumber);

    // Rows are [farmId, speciesId, count] of animals still on the farm.
    @Query("SELECT a.farmId, a.speciesId, COUNT(a) FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.deletedAt IS NULL AND a.status = 'ACTIVE' GROUP BY a.farmId, a.speciesId")
    List<Object[]> countActiveByFarmAndSpecies(TenantId tenantId);

    // Trends: which farm each (non-deleted) animal belongs to: [animalId, farmId]. Records of deleted animals are not attributed,
    // matching the cost reports, which only walk live animals.
    @Query("SELECT a.id, a.farmId FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.deletedAt IS NULL")
    List<Object[]> findAnimalFarms(TenantId tenantId);

    // Trends: animal purchases [acquisitionDate, acquisitionCost, farmId].
    @Query("SELECT a.acquisitionDate, a.acquisitionCost, a.farmId FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.deletedAt IS NULL AND a.acquisitionCost IS NOT NULL AND a.acquisitionDate >= :startDate AND a.acquisitionDate <= :endDate")
    List<Object[]> findPurchasesBetween(TenantId tenantId, LocalDate startDate, LocalDate endDate);
}

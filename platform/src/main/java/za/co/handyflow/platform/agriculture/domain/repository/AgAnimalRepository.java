package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgAnimal;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface AgAnimalRepository extends JpaRepository<AgAnimal, UUID> {

    @Query("SELECT a FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.id = :id AND a.deletedAt IS NULL")
    Optional<AgAnimal> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT a FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.deletedAt IS NULL ORDER BY a.tagNumber")
    Page<AgAnimal> findAllActiveForFarm(TenantId tenantId, UUID farmId, Pageable pageable);

    @Query("SELECT a FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.status = :status AND a.deletedAt IS NULL ORDER BY a.tagNumber")
    Page<AgAnimal> findByStatusForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable);

    @Query("SELECT a FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.productionAreaId = :productionAreaId AND a.deletedAt IS NULL ORDER BY a.tagNumber")
    Page<AgAnimal> findAllActiveForProductionArea(TenantId tenantId, UUID productionAreaId, Pageable pageable);

    // Backs the pre-insert uniqueness check in AgAnimalService.createAnimal() —
    // mirrors EarthAssetRepository.existsActiveByFleetNumber(), fronting the
    // DB-level uq_ag_animals_tenant_farm_tag unique index.
    @Query("SELECT COUNT(a) > 0 FROM AgAnimal a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.tagNumber = :tagNumber AND a.deletedAt IS NULL")
    boolean existsActiveByFarmAndTagNumber(TenantId tenantId, UUID farmId, String tagNumber);
}

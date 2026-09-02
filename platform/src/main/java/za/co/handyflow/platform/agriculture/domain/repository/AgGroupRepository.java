package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgGroup;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface AgGroupRepository extends JpaRepository<AgGroup, UUID> {

    @Query("SELECT g FROM AgGroup g WHERE g.tenantId = :tenantId AND g.id = :id AND g.deletedAt IS NULL")
    Optional<AgGroup> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT g FROM AgGroup g WHERE g.tenantId = :tenantId AND g.farmId = :farmId AND g.deletedAt IS NULL ORDER BY g.batchNumber")
    Page<AgGroup> findAllActiveForFarm(TenantId tenantId, UUID farmId, Pageable pageable);

    @Query("SELECT g FROM AgGroup g WHERE g.tenantId = :tenantId AND g.farmId = :farmId AND g.status = :status AND g.deletedAt IS NULL ORDER BY g.batchNumber")
    Page<AgGroup> findByStatusForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable);

    @Query("SELECT g FROM AgGroup g WHERE g.tenantId = :tenantId AND g.productionAreaId = :productionAreaId AND g.deletedAt IS NULL ORDER BY g.batchNumber")
    Page<AgGroup> findAllActiveForProductionArea(TenantId tenantId, UUID productionAreaId, Pageable pageable);

    // Backs the pre-insert uniqueness check in AgGroupService.createGroup() —
    // fronts the DB-level uq_ag_groups_tenant_farm_batch unique index.
    @Query("SELECT COUNT(g) > 0 FROM AgGroup g WHERE g.tenantId = :tenantId AND g.farmId = :farmId AND g.batchNumber = :batchNumber AND g.deletedAt IS NULL")
    boolean existsActiveByFarmAndBatchNumber(TenantId tenantId, UUID farmId, String batchNumber);
}

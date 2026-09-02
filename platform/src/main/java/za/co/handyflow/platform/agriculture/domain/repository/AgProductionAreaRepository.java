package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgProductionArea;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface AgProductionAreaRepository extends JpaRepository<AgProductionArea, UUID> {

    @Query("SELECT a FROM AgProductionArea a WHERE a.tenantId = :tenantId AND a.id = :id AND a.deletedAt IS NULL")
    Optional<AgProductionArea> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT a FROM AgProductionArea a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.deletedAt IS NULL ORDER BY a.name")
    Page<AgProductionArea> findAllActiveForFarm(TenantId tenantId, UUID farmId, Pageable pageable);

    @Query("SELECT a FROM AgProductionArea a WHERE a.tenantId = :tenantId AND a.farmId = :farmId AND a.status = :status AND a.deletedAt IS NULL ORDER BY a.name")
    Page<AgProductionArea> findByStatusForFarm(TenantId tenantId, UUID farmId, String status, Pageable pageable);
}

package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgEnterprise;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface AgEnterpriseRepository extends JpaRepository<AgEnterprise, UUID> {

    @Query("SELECT e FROM AgEnterprise e WHERE e.tenantId = :tenantId AND e.id = :id AND e.deletedAt IS NULL")
    Optional<AgEnterprise> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT e FROM AgEnterprise e WHERE e.tenantId = :tenantId AND e.farmId = :farmId AND e.deletedAt IS NULL ORDER BY e.name")
    Page<AgEnterprise> findAllActiveForFarm(TenantId tenantId, UUID farmId, Pageable pageable);
}

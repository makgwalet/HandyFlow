package za.co.handyflow.platform.agriculture.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.agriculture.domain.model.AgFarm;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface AgFarmRepository extends JpaRepository<AgFarm, UUID> {

    @Query("SELECT f FROM AgFarm f WHERE f.tenantId = :tenantId AND f.id = :id AND f.deletedAt IS NULL")
    Optional<AgFarm> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT f FROM AgFarm f WHERE f.tenantId = :tenantId AND f.deletedAt IS NULL ORDER BY f.name")
    Page<AgFarm> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT f FROM AgFarm f WHERE f.tenantId = :tenantId AND f.status = :status AND f.deletedAt IS NULL ORDER BY f.name")
    Page<AgFarm> findByStatus(TenantId tenantId, String status, Pageable pageable);
}

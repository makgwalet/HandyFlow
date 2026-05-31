package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrPayRun;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface HrPayRunRepository extends JpaRepository<HrPayRun, UUID> {

    @Query("SELECT r FROM HrPayRun r WHERE r.tenantId = :#{#tenantId.value} ORDER BY r.periodStart DESC")
    Page<HrPayRun> findAllByTenant(TenantId tenantId, Pageable pageable);

    @Query("SELECT r FROM HrPayRun r WHERE r.tenantId = :#{#tenantId.value} AND r.id = :id")
    Optional<HrPayRun> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(r) FROM HrPayRun r WHERE r.tenantId = :#{#tenantId.value}")
    long countByTenant(TenantId tenantId);
}
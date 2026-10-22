package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrPerfCycleRepository extends JpaRepository<HrPerfCycle, UUID> {

    @Query("SELECT c FROM HrPerfCycle c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id")
    Optional<HrPerfCycle> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT c FROM HrPerfCycle c WHERE c.tenantId = :#{#tenantId.value} ORDER BY c.periodStart DESC, c.createdAt DESC")
    List<HrPerfCycle> forTenant(TenantId tenantId);
}

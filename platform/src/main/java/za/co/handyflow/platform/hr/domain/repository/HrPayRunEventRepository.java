package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrPayRunEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface HrPayRunEventRepository extends JpaRepository<HrPayRunEvent, UUID> {

    @Query("SELECT e FROM HrPayRunEvent e WHERE e.tenantId = :#{#tenantId.value} AND e.payRunId = :payRunId ORDER BY e.createdAt")
    List<HrPayRunEvent> forPayRun(TenantId tenantId, UUID payRunId);
}

package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrWorkSchedule;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrWorkScheduleRepository extends JpaRepository<HrWorkSchedule, UUID> {

    @Query("SELECT s FROM HrWorkSchedule s WHERE s.tenantId = :#{#tenantId.value} ORDER BY s.name")
    List<HrWorkSchedule> forTenant(TenantId tenantId);

    @Query("SELECT s FROM HrWorkSchedule s WHERE s.tenantId = :#{#tenantId.value} AND s.id = :id")
    Optional<HrWorkSchedule> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT s FROM HrWorkSchedule s WHERE s.tenantId = :#{#tenantId.value} AND s.defaultSchedule = true AND s.active = true")
    Optional<HrWorkSchedule> defaultFor(TenantId tenantId);
}

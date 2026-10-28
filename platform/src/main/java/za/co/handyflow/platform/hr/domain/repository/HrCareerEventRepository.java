package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrCareerEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrCareerEventRepository extends JpaRepository<HrCareerEvent, UUID> {

    @Query("SELECT c FROM HrCareerEvent c WHERE c.tenantId = :#{#tenantId.value} AND c.employeeId = :employeeId AND c.removedAt IS NULL ORDER BY c.eventDate DESC, c.createdAt DESC")
    List<HrCareerEvent> forEmployee(TenantId tenantId, UUID employeeId);

    @Query("SELECT c FROM HrCareerEvent c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id AND c.removedAt IS NULL")
    Optional<HrCareerEvent> findOne(TenantId tenantId, UUID id);
}

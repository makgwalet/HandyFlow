package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrErCase;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrErCaseRepository extends JpaRepository<HrErCase, UUID> {

    @Query("SELECT c FROM HrErCase c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id")
    Optional<HrErCase> findByTenantAndId(TenantId tenantId, UUID id);

    /** Every case of the company, newest first. Type, status and who may see which are applied in the service. */
    @Query("SELECT c FROM HrErCase c WHERE c.tenantId = :#{#tenantId.value} ORDER BY c.reportedOn DESC, c.createdAt DESC")
    List<HrErCase> forTenant(TenantId tenantId);

    @Query("SELECT c FROM HrErCase c WHERE c.tenantId = :#{#tenantId.value} AND c.employeeId = :employeeId ORDER BY c.reportedOn DESC, c.createdAt DESC")
    List<HrErCase> forEmployee(TenantId tenantId, UUID employeeId);

    /** Every case of every company that is not closed or withdrawn, for the daily reminders. */
    @Query("SELECT c FROM HrErCase c WHERE c.status NOT IN ('CLOSED', 'WITHDRAWN')")
    List<HrErCase> allOpen();
}

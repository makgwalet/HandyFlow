package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeAudit;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface HrEmployeeAuditRepository extends JpaRepository<HrEmployeeAudit, UUID> {

    @Query("SELECT a FROM HrEmployeeAudit a WHERE a.tenantId = :#{#tenantId.value} AND a.employeeId = :employeeId ORDER BY a.createdAt DESC")
    List<HrEmployeeAudit> forEmployee(TenantId tenantId, UUID employeeId);

    @Query("""
            SELECT COUNT(a) FROM HrEmployeeAudit a WHERE a.tenantId = :#{#tenantId.value} AND a.employeeId = :employeeId
              AND a.actorId = :actorId AND a.action = :action AND a.createdAt > :since""")
    long countSince(TenantId tenantId, UUID employeeId, UUID actorId, String action, Instant since);
}

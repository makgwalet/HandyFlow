package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeChange;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrEmployeeChangeRepository extends JpaRepository<HrEmployeeChange, UUID> {

    @Query("SELECT c FROM HrEmployeeChange c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id")
    Optional<HrEmployeeChange> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT c FROM HrEmployeeChange c WHERE c.tenantId = :#{#tenantId.value} AND c.employeeId = :employeeId ORDER BY c.requestedAt DESC")
    List<HrEmployeeChange> forEmployee(TenantId tenantId, UUID employeeId);

    @Query("SELECT c FROM HrEmployeeChange c WHERE c.tenantId = :#{#tenantId.value} AND c.status = 'PENDING' ORDER BY c.requestedAt")
    List<HrEmployeeChange> pending(TenantId tenantId);

    @Query("""
            SELECT COUNT(c) FROM HrEmployeeChange c WHERE c.tenantId = :#{#tenantId.value} AND c.employeeId = :employeeId
              AND c.changeType = :type AND c.status = 'PENDING'""")
    long countPending(TenantId tenantId, UUID employeeId, String type);

    /** Approved changes whose date has come, oldest first, so a later one wins. */
    @Query("""
            SELECT c FROM HrEmployeeChange c WHERE c.tenantId = :#{#tenantId.value} AND c.status = 'APPROVED'
              AND c.effectiveFrom <= :day ORDER BY c.effectiveFrom, c.decidedAt""")
    List<HrEmployeeChange> due(TenantId tenantId, LocalDate day);

    /** Changes applied to these employees after the given moment. Used to warn that a calculated run is out of date. */
    @Query("""
            SELECT c FROM HrEmployeeChange c WHERE c.tenantId = :#{#tenantId.value} AND c.employeeId IN :employeeIds
              AND c.status = 'APPLIED' AND c.appliedAt > :since""")
    List<HrEmployeeChange> appliedSince(TenantId tenantId, Collection<UUID> employeeIds, Instant since);
}

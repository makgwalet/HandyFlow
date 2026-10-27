package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrPayAdjustment;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrPayAdjustmentRepository extends JpaRepository<HrPayAdjustment, UUID> {

    @Query("SELECT a FROM HrPayAdjustment a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<HrPayAdjustment> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT a FROM HrPayAdjustment a WHERE a.tenantId = :#{#tenantId.value} AND a.employeeId = :employeeId ORDER BY a.createdAt DESC")
    List<HrPayAdjustment> forEmployee(TenantId tenantId, UUID employeeId);

    /** Newest first. A null status means every status. */
    @Query("""
            SELECT a FROM HrPayAdjustment a WHERE a.tenantId = :#{#tenantId.value}
              AND (:status IS NULL OR a.status = :status) ORDER BY a.createdAt DESC""")
    List<HrPayAdjustment> byStatus(TenantId tenantId, String status);

    /** Open items a pay run can pay: dated up to the end of the period and not taken by another run. */
    @Query("""
            SELECT a FROM HrPayAdjustment a WHERE a.tenantId = :#{#tenantId.value} AND a.status = 'OPEN'
              AND a.payFrom <= :periodEnd AND (a.payRunId IS NULL OR a.payRunId = :payRunId)""")
    List<HrPayAdjustment> payableIn(TenantId tenantId, LocalDate periodEnd, UUID payRunId);

    @Query("SELECT a FROM HrPayAdjustment a WHERE a.tenantId = :#{#tenantId.value} AND a.payRunId = :payRunId")
    List<HrPayAdjustment> takenBy(TenantId tenantId, UUID payRunId);
}

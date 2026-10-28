package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrLeaveRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrLeaveRequestRepository extends JpaRepository<HrLeaveRequest, UUID> {

    @Query("""
        SELECT r FROM HrLeaveRequest r
        WHERE r.tenantId = :#{#tenantId.value}
        AND (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt DESC
        """)
    Page<HrLeaveRequest> findAllByTenant(TenantId tenantId, String status, Pageable pageable);

    @Query("SELECT r FROM HrLeaveRequest r WHERE r.employeeId = :employeeId ORDER BY r.createdAt DESC")
    Page<HrLeaveRequest> findByEmployee(UUID employeeId, Pageable pageable);

    @Query("SELECT r FROM HrLeaveRequest r WHERE r.tenantId = :#{#tenantId.value} AND r.id = :id")
    Optional<HrLeaveRequest> findByTenantAndId(TenantId tenantId, UUID id);

    /** Open requests of the employee (waiting or approved) that touch the dates. Used to refuse overlapping leave. */
    @Query("""
            SELECT r FROM HrLeaveRequest r WHERE r.tenantId = :#{#tenantId.value} AND r.employeeId = :employeeId
              AND r.status IN ('PENDING', 'APPROVED') AND r.startDate <= :to AND r.endDate >= :from""")
    List<HrLeaveRequest> overlapping(TenantId tenantId, UUID employeeId, java.time.LocalDate from, java.time.LocalDate to);

    /** Approved unpaid leave that touches a pay period. Payroll does not deduct it yet, so the run review says so. */
    @Query("""
            SELECT r FROM HrLeaveRequest r WHERE r.tenantId = :#{#tenantId.value} AND r.status = 'APPROVED'
              AND r.leaveType = 'UNPAID' AND r.startDate <= :to AND r.endDate >= :from""")
    List<HrLeaveRequest> approvedUnpaidIn(TenantId tenantId, java.time.LocalDate from, java.time.LocalDate to);

    /** Waiting or approved requests of an employee that run past a date. Used when employment ends. */
    @Query("""
            SELECT r FROM HrLeaveRequest r WHERE r.tenantId = :#{#tenantId.value} AND r.employeeId = :employeeId
              AND r.status IN ('PENDING', 'APPROVED') AND r.endDate > :after""")
    List<HrLeaveRequest> openEndingAfter(TenantId tenantId, UUID employeeId, java.time.LocalDate after);
}

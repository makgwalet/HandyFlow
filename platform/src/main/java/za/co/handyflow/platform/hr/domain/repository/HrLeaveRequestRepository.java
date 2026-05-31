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
    List<HrLeaveRequest> findByEmployee(UUID employeeId);

    @Query("SELECT r FROM HrLeaveRequest r WHERE r.tenantId = :#{#tenantId.value} AND r.id = :id")
    Optional<HrLeaveRequest> findByTenantAndId(TenantId tenantId, UUID id);
}
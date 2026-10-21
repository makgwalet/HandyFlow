package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrLeaveLedger;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrLeaveLedgerRepository extends JpaRepository<HrLeaveLedger, UUID> {

    /** Entries of one leave type dated within a window. */
    @Query("""
            SELECT l FROM HrLeaveLedger l WHERE l.tenantId = :#{#tenantId.value} AND l.employeeId = :employeeId
              AND l.leaveType = :leaveType AND l.entryDate BETWEEN :from AND :to ORDER BY l.entryDate, l.createdAt""")
    List<HrLeaveLedger> window(TenantId tenantId, UUID employeeId, String leaveType, LocalDate from, LocalDate to);

    /** Every entry of an employee dated within a window, newest first, for the statement. */
    @Query("""
            SELECT l FROM HrLeaveLedger l WHERE l.tenantId = :#{#tenantId.value} AND l.employeeId = :employeeId
              AND l.entryDate BETWEEN :from AND :to ORDER BY l.entryDate DESC, l.createdAt DESC""")
    List<HrLeaveLedger> between(TenantId tenantId, UUID employeeId, LocalDate from, LocalDate to);

    @Query("SELECT l FROM HrLeaveLedger l WHERE l.tenantId = :#{#tenantId.value} AND l.requestId = :requestId ORDER BY l.createdAt")
    List<HrLeaveLedger> forRequest(TenantId tenantId, UUID requestId);

    @Query("""
            SELECT COUNT(l) FROM HrLeaveLedger l WHERE l.tenantId = :#{#tenantId.value} AND l.employeeId = :employeeId
              AND l.leaveType = :leaveType AND l.kind = :kind AND l.periodKey = :periodKey""")
    long countKey(TenantId tenantId, UUID employeeId, String leaveType, String kind, String periodKey);

    /** The first date this employee has any entry of the type, so carry forward can chain from there. */
    @Query("SELECT MIN(l.entryDate) FROM HrLeaveLedger l WHERE l.tenantId = :#{#tenantId.value} AND l.employeeId = :employeeId AND l.leaveType = :leaveType")
    Optional<LocalDate> firstEntry(TenantId tenantId, UUID employeeId, String leaveType);
}

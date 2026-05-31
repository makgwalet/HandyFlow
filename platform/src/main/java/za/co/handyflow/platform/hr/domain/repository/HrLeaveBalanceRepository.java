package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrLeaveBalance;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrLeaveBalanceRepository extends JpaRepository<HrLeaveBalance, UUID> {

    @Query("SELECT b FROM HrLeaveBalance b WHERE b.employeeId = :employeeId AND b.leaveYear = :year")
    List<HrLeaveBalance> findByEmployeeAndYear(UUID employeeId, int year);

    @Query("SELECT b FROM HrLeaveBalance b WHERE b.employeeId = :employeeId AND b.leaveYear = :year AND b.leaveType = :leaveType")
    Optional<HrLeaveBalance> findByEmployeeYearAndType(UUID employeeId, int year, String leaveType);
}
package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrPayslip;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrPayslipRepository extends JpaRepository<HrPayslip, UUID> {

    @Query("SELECT p FROM HrPayslip p WHERE p.payRunId = :payRunId ORDER BY p.createdAt")
    List<HrPayslip> findByPayRun(UUID payRunId);

    @Query("SELECT p FROM HrPayslip p WHERE p.employeeId = :employeeId ORDER BY p.createdAt DESC")
    List<HrPayslip> findByEmployee(UUID employeeId);

    @Query("SELECT p FROM HrPayslip p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<HrPayslip> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("""
        SELECT COALESCE(SUM(p.totalEarnings), 0) FROM HrPayslip p
        WHERE p.employeeId = :employeeId
        AND p.taxYear = :taxYear
        AND p.id != :excludePayslipId
        """)
    java.math.BigDecimal sumYtdGross(UUID employeeId, int taxYear, UUID excludePayslipId);

    @Query("""
        SELECT COALESCE(SUM(p.payeAmount), 0) FROM HrPayslip p
        WHERE p.employeeId = :employeeId
        AND p.taxYear = :taxYear
        AND p.id != :excludePayslipId
        """)
    java.math.BigDecimal sumYtdPaye(UUID employeeId, int taxYear, UUID excludePayslipId);

    @Query("""
    SELECT COALESCE(SUM(p.uifEmployee), 0) FROM HrPayslip p
    WHERE p.employeeId = :employeeId
    AND p.taxYear = :taxYear
    AND p.id != :excludePayslipId
    """)
    java.math.BigDecimal sumYtdUif(UUID employeeId, int taxYear, UUID excludePayslipId);
}
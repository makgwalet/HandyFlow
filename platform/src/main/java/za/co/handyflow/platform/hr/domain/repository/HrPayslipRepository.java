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
        AND p.payRunId NOT IN (SELECT r.id FROM HrPayRun r WHERE r.status = 'CANCELLED')
        """)
    java.math.BigDecimal sumYtdGross(UUID employeeId, int taxYear, UUID excludePayslipId);

    @Query("""
        SELECT COALESCE(SUM(p.payeAmount), 0) FROM HrPayslip p
        WHERE p.employeeId = :employeeId
        AND p.taxYear = :taxYear
        AND p.id != :excludePayslipId
        AND p.payRunId NOT IN (SELECT r.id FROM HrPayRun r WHERE r.status = 'CANCELLED')
        """)
    java.math.BigDecimal sumYtdPaye(UUID employeeId, int taxYear, UUID excludePayslipId);

    @Query("""
    SELECT COALESCE(SUM(p.uifEmployee), 0) FROM HrPayslip p
    WHERE p.employeeId = :employeeId
    AND p.taxYear = :taxYear
    AND p.id != :excludePayslipId
    AND p.payRunId NOT IN (SELECT r.id FROM HrPayRun r WHERE r.status = 'CANCELLED')
    """)
    java.math.BigDecimal sumYtdUif(UUID employeeId, int taxYear, UUID excludePayslipId);

    @Query("""
        SELECT COALESCE(SUM(p.regularTaxable), 0) FROM HrPayslip p
        WHERE p.employeeId = :employeeId
        AND p.taxYear = :taxYear
        AND p.payRunId IN (SELECT r.id FROM HrPayRun r WHERE r.status <> 'CANCELLED' AND r.payDate <= :before AND r.id <> :exceptRunId)
        """)
    java.math.BigDecimal sumRegularTaxable(UUID employeeId, int taxYear, java.time.LocalDate before, UUID exceptRunId);

    @Query("""
        SELECT COALESCE(SUM(p.payeRegular), 0) FROM HrPayslip p
        WHERE p.employeeId = :employeeId
        AND p.taxYear = :taxYear
        AND p.payRunId IN (SELECT r.id FROM HrPayRun r WHERE r.status <> 'CANCELLED' AND r.payDate <= :before AND r.id <> :exceptRunId)
        """)
    java.math.BigDecimal sumRegularPaye(UUID employeeId, int taxYear, java.time.LocalDate before, UUID exceptRunId);

    /** The incentive worked out across the payslips of the given runs. */
    @Query("SELECT COALESCE(SUM(p.etiAmount), 0) FROM HrPayslip p WHERE p.payRunId IN :runIds")
    java.math.BigDecimal sumEti(java.util.Collection<UUID> runIds);

    /** Qualifying ETI months already used by payslips of earlier pay runs. */
    @Query("""
        SELECT COUNT(p) FROM HrPayslip p
        WHERE p.employeeId = :employeeId
        AND p.etiMonth IS NOT NULL
        AND p.payRunId IN (SELECT r.id FROM HrPayRun r WHERE r.status <> 'CANCELLED' AND r.payDate < :before)
        """)
    long etiMonthsBefore(UUID employeeId, java.time.LocalDate before);

    /** ETI already claimed for the person in a run whose period ends within the given month, other than the run being calculated. */
    @Query("""
        SELECT COUNT(p) FROM HrPayslip p
        WHERE p.employeeId = :employeeId
        AND p.etiMonth IS NOT NULL
        AND p.payRunId IN (SELECT r.id FROM HrPayRun r WHERE r.status <> 'CANCELLED' AND r.id <> :exceptRunId
                           AND r.periodEnd >= :monthStart AND r.periodEnd <= :monthEnd)
        """)
    long etiClaimedInMonth(UUID employeeId, java.time.LocalDate monthStart, java.time.LocalDate monthEnd, UUID exceptRunId);

    @Query("SELECT p FROM HrPayslip p WHERE p.payRunId IN :runIds ORDER BY p.createdAt")
    List<HrPayslip> findByPayRuns(java.util.Collection<UUID> runIds);
}

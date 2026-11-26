package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.Payslip;

import java.util.List;
import java.util.UUID;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    /**
     * What UIF was already charged on for this employee in the calendar month of the run being processed: the sum of the
     * recorded UIF bases of their payslips in other processed runs whose pay date falls in the same month.
     */
    @Query("""
        SELECT COALESCE(SUM(p.uifBase), 0) FROM Payslip p, PayRun r
        WHERE p.payRunId = r.id AND p.tenantId = :tenantId AND p.payEmployeeId = :employeeId
          AND r.status = 'PROCESSED' AND r.id <> :excludeRunId
          AND r.payDate >= :monthStart AND r.payDate <= :monthEnd
    """)
    java.math.BigDecimal sumUifBaseInMonth(@Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId,
                                           @Param("excludeRunId") UUID excludeRunId,
                                           @Param("monthStart") java.time.LocalDate monthStart,
                                           @Param("monthEnd") java.time.LocalDate monthEnd);


    @Query("SELECT p FROM Payslip p WHERE p.tenantId = :tenantId AND p.payRunId = :payRunId")
    List<Payslip> findByPayRun(@Param("tenantId") UUID tenantId, @Param("payRunId") UUID payRunId);
}
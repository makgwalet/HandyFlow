package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmp201EtiLine;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PayEmp201EtiLineRepository extends JpaRepository<PayEmp201EtiLine, UUID> {

    @Query("SELECT l FROM PayEmp201EtiLine l WHERE l.tenantId = :tenantId AND l.emp201ReturnId = :returnId")
    List<PayEmp201EtiLine> findByReturn(@Param("tenantId") UUID tenantId, @Param("returnId") UUID returnId);

    @Modifying
    @Query("DELETE FROM PayEmp201EtiLine l WHERE l.tenantId = :tenantId AND l.emp201ReturnId = :returnId")
    void deleteByReturn(@Param("tenantId") UUID tenantId, @Param("returnId") UUID returnId);

    /** Months this employee has actually had an incentive claimed, in returns for months before the given one. */
    @Query("""
        SELECT COUNT(l) FROM PayEmp201EtiLine l, PayEmp201Return r
        WHERE l.emp201ReturnId = r.id AND l.tenantId = :tenantId AND l.payEmployeeId = :employeeId
          AND l.eligible = true AND l.incentive > 0 AND r.etiClaimed = true AND r.taxMonth < :month
    """)
    long monthsClaimedBefore(@Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId, @Param("month") LocalDate month);
}

package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayslipLine;

import java.util.List;
import java.util.UUID;

public interface PayslipLineRepository extends JpaRepository<PayslipLine, UUID> {

    @Query("SELECT l FROM PayslipLine l WHERE l.tenantId = :tenantId AND l.payslipId = :payslipId ORDER BY l.role, l.name")
    List<PayslipLine> findByPayslip(@Param("tenantId") UUID tenantId, @Param("payslipId") UUID payslipId);
}

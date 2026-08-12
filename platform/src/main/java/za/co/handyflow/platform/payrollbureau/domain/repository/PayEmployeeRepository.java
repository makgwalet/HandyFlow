package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployee;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayEmployeeRepository extends JpaRepository<PayEmployee, UUID> {

    @Query("""
        SELECT e FROM PayEmployee e
        WHERE e.payClientId = :payClientId AND e.status = 'ACTIVE' AND e.deletedAt IS NULL
        ORDER BY e.lastName ASC
    """)
    List<PayEmployee> findActiveByClient(@Param("payClientId") UUID payClientId);

    @Query("""
        SELECT e FROM PayEmployee e
        WHERE e.tenantId = :tenantId AND e.id = :id AND e.deletedAt IS NULL
    """)
    Optional<PayEmployee> findActiveById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Sum of all active employees' annual gross for one client — the SDL threshold check. */
    @Query("""
        SELECT COALESCE(SUM(e.grossSalary), 0) FROM PayEmployee e
        WHERE e.payClientId = :payClientId AND e.status = 'ACTIVE' AND e.deletedAt IS NULL
    """)
    BigDecimal sumMonthlyGrossByClient(@Param("payClientId") UUID payClientId);
}
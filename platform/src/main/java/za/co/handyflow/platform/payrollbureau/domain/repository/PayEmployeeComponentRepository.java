package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmployeeComponent;

import java.util.List;
import java.util.UUID;

public interface PayEmployeeComponentRepository extends JpaRepository<PayEmployeeComponent, UUID> {

    @Query("SELECT e FROM PayEmployeeComponent e WHERE e.tenantId = :tenantId AND e.payEmployeeId = :employeeId")
    List<PayEmployeeComponent> findByEmployee(@Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    /** Every employee's recurring components for one client: what a pay run needs, in one query. */
    @Query("""
        SELECT x FROM PayEmployeeComponent x, PayEmployee e
        WHERE x.payEmployeeId = e.id AND x.tenantId = :tenantId AND e.payClientId = :payClientId
    """)
    List<PayEmployeeComponent> findByClient(@Param("tenantId") UUID tenantId, @Param("payClientId") UUID payClientId);
}

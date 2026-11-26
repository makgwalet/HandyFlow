package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayRunItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayRunItemRepository extends JpaRepository<PayRunItem, UUID> {

    @Query("SELECT i FROM PayRunItem i WHERE i.tenantId = :tenantId AND i.payRunId = :payRunId")
    List<PayRunItem> findByRun(@Param("tenantId") UUID tenantId, @Param("payRunId") UUID payRunId);

    @Query("SELECT i FROM PayRunItem i WHERE i.tenantId = :tenantId AND i.payRunId = :payRunId AND i.id = :id")
    Optional<PayRunItem> findByRunAndId(@Param("tenantId") UUID tenantId, @Param("payRunId") UUID payRunId, @Param("id") UUID id);

    @Query("""
        SELECT i FROM PayRunItem i
        WHERE i.tenantId = :tenantId AND i.payRunId = :payRunId AND i.payEmployeeId = :employeeId AND i.componentId = :componentId
    """)
    Optional<PayRunItem> findOne(@Param("tenantId") UUID tenantId, @Param("payRunId") UUID payRunId,
                                 @Param("employeeId") UUID employeeId, @Param("componentId") UUID componentId);
}

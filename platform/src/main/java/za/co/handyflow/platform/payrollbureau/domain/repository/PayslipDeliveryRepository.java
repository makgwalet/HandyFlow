package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayslipDelivery;

import java.util.List;
import java.util.UUID;

public interface PayslipDeliveryRepository extends JpaRepository<PayslipDelivery, UUID> {

    @Query("SELECT d FROM PayslipDelivery d WHERE d.tenantId = :tenantId AND d.payRunId = :payRunId")
    List<PayslipDelivery> findByRun(@Param("tenantId") UUID tenantId, @Param("payRunId") UUID payRunId);
}

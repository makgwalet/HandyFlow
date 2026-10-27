package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrPayRunPayment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrPayRunPaymentRepository extends JpaRepository<HrPayRunPayment, UUID> {

    @Query("SELECT p FROM HrPayRunPayment p WHERE p.tenantId = :#{#tenantId.value} AND p.payRunId = :payRunId ORDER BY p.beneficiaryName")
    List<HrPayRunPayment> forRun(TenantId tenantId, UUID payRunId);

    @Query("SELECT p FROM HrPayRunPayment p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<HrPayRunPayment> findByTenantAndId(TenantId tenantId, UUID id);
}

package za.co.handyflow.platform.ap.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.ap.domain.model.ApBillPayment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ApBillPaymentRepository extends JpaRepository<ApBillPayment, UUID> {

    @Query("SELECT p FROM ApBillPayment p WHERE p.tenantId = :tenantId AND p.billId = :billId ORDER BY p.createdAt ASC")
    List<ApBillPayment> findByBill(TenantId tenantId, UUID billId);

    @Query("SELECT p FROM ApBillPayment p WHERE p.tenantId = :tenantId AND p.batchId = :batchId")
    List<ApBillPayment> findByBatch(TenantId tenantId, UUID batchId);
}

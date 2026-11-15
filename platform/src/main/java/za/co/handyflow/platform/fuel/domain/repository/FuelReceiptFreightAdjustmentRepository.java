package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelReceiptFreightAdjustment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelReceiptFreightAdjustmentRepository extends JpaRepository<FuelReceiptFreightAdjustment, UUID> {

    @Query("SELECT a FROM FuelReceiptFreightAdjustment a WHERE a.tenantId = :tenantId AND a.receiptId = :receiptId ORDER BY a.createdAt ASC")
    List<FuelReceiptFreightAdjustment> findForReceipt(TenantId tenantId, UUID receiptId);
}

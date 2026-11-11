package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelSupplierInvoiceReceipt;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FuelSupplierInvoiceReceiptRepository extends JpaRepository<FuelSupplierInvoiceReceipt, UUID> {
    @Query("SELECT l FROM FuelSupplierInvoiceReceipt l WHERE l.tenantId = :tenantId AND l.invoiceId IN :invoiceIds")
    List<FuelSupplierInvoiceReceipt> findByInvoices(TenantId tenantId, Collection<UUID> invoiceIds);

    @Query("SELECT l.receiptId FROM FuelSupplierInvoiceReceipt l WHERE l.tenantId = :tenantId AND l.receiptId IN :receiptIds")
    List<UUID> findAlreadyLinked(TenantId tenantId, Collection<UUID> receiptIds);
}

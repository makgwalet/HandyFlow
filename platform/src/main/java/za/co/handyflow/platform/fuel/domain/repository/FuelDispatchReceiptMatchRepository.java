package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelDispatchReceiptMatch;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelDispatchReceiptMatchRepository extends JpaRepository<FuelDispatchReceiptMatch, UUID> {

    @Query("SELECT m FROM FuelDispatchReceiptMatch m WHERE m.tenantId = :tenantId AND (m.dispatchId IN :dispatchIds OR m.receiptId IN :receiptIds)")
    List<FuelDispatchReceiptMatch> findForAny(TenantId tenantId, Collection<UUID> dispatchIds, Collection<UUID> receiptIds);

    @Query("SELECT m FROM FuelDispatchReceiptMatch m WHERE m.tenantId = :tenantId AND m.receiptId = :receiptId")
    Optional<FuelDispatchReceiptMatch> findByReceipt(TenantId tenantId, UUID receiptId);

    @Query("SELECT m FROM FuelDispatchReceiptMatch m WHERE m.tenantId = :tenantId AND m.id = :id")
    Optional<FuelDispatchReceiptMatch> findOne(TenantId tenantId, UUID id);
}

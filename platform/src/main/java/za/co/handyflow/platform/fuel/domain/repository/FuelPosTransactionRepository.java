package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelPosTransaction;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelPosTransactionRepository extends JpaRepository<FuelPosTransaction, UUID> {

    @Query("SELECT t FROM FuelPosTransaction t WHERE t.tenantId = :tenantId AND t.source = :source AND t.externalId = :externalId")
    Optional<FuelPosTransaction> findByRef(TenantId tenantId, String source, String externalId);

    @Query("SELECT t FROM FuelPosTransaction t WHERE t.tenantId = :tenantId AND t.source = :source AND t.shiftRef = :shiftRef ORDER BY t.receivedAt, t.externalId")
    List<FuelPosTransaction> findByShiftRef(TenantId tenantId, String source, String shiftRef);
}

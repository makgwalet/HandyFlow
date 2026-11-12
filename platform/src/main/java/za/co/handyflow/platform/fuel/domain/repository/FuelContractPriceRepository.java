package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelContractPrice;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelContractPriceRepository extends JpaRepository<FuelContractPrice, FuelContractPrice.Key> {

    @Query("SELECT l FROM FuelContractPrice l WHERE l.tenantId = :tenantId AND l.contractId = :contractId ORDER BY l.linkedAt ASC")
    List<FuelContractPrice> findByContract(TenantId tenantId, UUID contractId);

    @Query("SELECT l FROM FuelContractPrice l WHERE l.tenantId = :tenantId AND l.contractId = :contractId AND l.priceId = :priceId")
    Optional<FuelContractPrice> findLink(TenantId tenantId, UUID contractId, UUID priceId);

    @Query("SELECT l FROM FuelContractPrice l WHERE l.tenantId = :tenantId AND l.priceId = :priceId")
    List<FuelContractPrice> findByPrice(TenantId tenantId, UUID priceId);
}

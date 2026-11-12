package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelContract;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelContractRepository extends JpaRepository<FuelContract, UUID> {

    @Query("SELECT c FROM FuelContract c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL ORDER BY c.endDate ASC, c.contractNumber ASC")
    List<FuelContract> findAllActive(TenantId tenantId);

    @Query("SELECT c FROM FuelContract c WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL")
    Optional<FuelContract> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(c) > 0 FROM FuelContract c WHERE c.tenantId = :tenantId AND lower(c.contractNumber) = lower(:number) AND c.deletedAt IS NULL")
    boolean numberTaken(TenantId tenantId, String number);
}

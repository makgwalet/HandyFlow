package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelEnforcement;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelEnforcementRepository extends JpaRepository<FuelEnforcement, UUID> {

    @Query("SELECT e FROM FuelEnforcement e WHERE e.tenantId = :tenantId AND e.control = :control")
    Optional<FuelEnforcement> find(TenantId tenantId, String control);

    @Query("SELECT e FROM FuelEnforcement e WHERE e.tenantId = :tenantId ORDER BY e.control")
    List<FuelEnforcement> forTenant(TenantId tenantId);
}

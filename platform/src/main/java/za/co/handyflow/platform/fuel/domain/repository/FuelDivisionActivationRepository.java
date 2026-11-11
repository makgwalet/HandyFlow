package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelDivisionActivation;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelDivisionActivationRepository extends JpaRepository<FuelDivisionActivation, UUID> {
    @Query("SELECT a FROM FuelDivisionActivation a WHERE a.tenantId = :tenantId")
    List<FuelDivisionActivation> findByTenant(TenantId tenantId);
}

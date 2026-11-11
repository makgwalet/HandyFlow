package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelOperatingProfile;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FuelOperatingProfileRepository extends JpaRepository<FuelOperatingProfile, UUID> {
    @Query("SELECT p FROM FuelOperatingProfile p WHERE p.tenantId = :tenantId")
    Optional<FuelOperatingProfile> findByTenant(TenantId tenantId);
}

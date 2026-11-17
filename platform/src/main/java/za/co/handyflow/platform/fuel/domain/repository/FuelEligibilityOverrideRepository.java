package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelEligibilityOverride;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface FuelEligibilityOverrideRepository extends JpaRepository<FuelEligibilityOverride, UUID> {

    @Query("SELECT o FROM FuelEligibilityOverride o WHERE o.tenantId = :tenantId ORDER BY o.createdAt DESC")
    List<FuelEligibilityOverride> findRecent(TenantId tenantId, Pageable page);
}

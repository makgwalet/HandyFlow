package za.co.handyflow.platform.debtcollection.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.debtcollection.domain.model.AllocationSettings;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllocationSettingsRepository extends JpaRepository<AllocationSettings, UUID> {

    @Query("SELECT s FROM AllocationSettings s WHERE s.tenantId = :tenantId")
    Optional<AllocationSettings> findForTenant(TenantId tenantId);

    /** Cross-tenant: tenants that opted in to automatic allocation (the scheduler's worklist). */
    @Query("SELECT s FROM AllocationSettings s WHERE s.enabled = true")
    List<AllocationSettings> findAllEnabled();
}

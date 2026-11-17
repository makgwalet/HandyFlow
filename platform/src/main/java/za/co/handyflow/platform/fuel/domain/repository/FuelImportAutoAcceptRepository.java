package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelImportAutoAccept;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FuelImportAutoAcceptRepository extends JpaRepository<FuelImportAutoAccept, UUID> {

    @Query("SELECT a FROM FuelImportAutoAccept a WHERE a.tenantId = :tenantId")
    Optional<FuelImportAutoAccept> findForTenant(TenantId tenantId);
}

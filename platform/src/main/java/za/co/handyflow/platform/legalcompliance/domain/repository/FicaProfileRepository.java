package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FicaProfileRepository extends JpaRepository<FicaProfile, UUID> {

    @Query("SELECT x FROM FicaProfile x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<FicaProfile> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM FicaProfile x WHERE x.tenantId = :tenantId")
    Optional<FicaProfile> findByTenant(TenantId tenantId);
}

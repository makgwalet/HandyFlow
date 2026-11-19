package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FicaBeneficialOwnerRepository extends JpaRepository<FicaBeneficialOwner, UUID> {

    @Query("SELECT x FROM FicaBeneficialOwner x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<FicaBeneficialOwner> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM FicaBeneficialOwner x WHERE x.tenantId = :tenantId AND x.partyId = :partyId ORDER BY x.createdAt")
    List<FicaBeneficialOwner> findForParty(TenantId tenantId, UUID partyId);
}

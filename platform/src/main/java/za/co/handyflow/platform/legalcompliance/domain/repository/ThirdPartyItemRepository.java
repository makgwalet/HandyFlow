package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThirdPartyItemRepository extends JpaRepository<ThirdPartyItem, UUID> {

    @Query("SELECT x FROM ThirdPartyItem x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<ThirdPartyItem> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM ThirdPartyItem x WHERE x.tenantId = :tenantId AND x.thirdPartyId = :thirdPartyId ORDER BY x.createdAt")
    List<ThirdPartyItem> findForParty(TenantId tenantId, UUID thirdPartyId);

    @Query("SELECT x FROM ThirdPartyItem x WHERE x.tenantId = :tenantId AND x.thirdPartyId IN :ids")
    List<ThirdPartyItem> findForParties(TenantId tenantId, Collection<UUID> ids);

    /** Cross-tenant — daily sweep: verified items that expire on or before the horizon. */
    @Query("SELECT x FROM ThirdPartyItem x WHERE x.status = za.co.handyflow.platform.legalcompliance.domain.model.ItemStatus.VERIFIED AND x.validTo IS NOT NULL AND x.validTo <= :horizon")
    List<ThirdPartyItem> findExpiringAcrossTenants(LocalDate horizon);
}

package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.DeclarationReturn;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeclarationReturnRepository extends JpaRepository<DeclarationReturn, UUID> {

    @Query("SELECT x FROM DeclarationReturn x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<DeclarationReturn> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM DeclarationReturn x WHERE x.tenantId = :tenantId AND x.campaignId = :campaignId ORDER BY x.userName")
    List<DeclarationReturn> findByCampaign(TenantId tenantId, UUID campaignId);

    @Query("SELECT x FROM DeclarationReturn x WHERE x.tenantId = :tenantId AND x.userId = :userId ORDER BY x.createdAt DESC")
    List<DeclarationReturn> findForUser(TenantId tenantId, UUID userId);

    @Query("SELECT x FROM DeclarationReturn x WHERE x.tenantId = :tenantId AND x.campaignId = :campaignId AND x.status = za.co.handyflow.platform.legalcompliance.domain.model.ReturnStatus.PENDING")
    List<DeclarationReturn> findPending(TenantId tenantId, UUID campaignId);
}

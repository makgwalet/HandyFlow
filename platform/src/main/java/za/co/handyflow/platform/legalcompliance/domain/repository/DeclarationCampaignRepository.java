package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.CampaignStatus;
import za.co.handyflow.platform.legalcompliance.domain.model.DeclarationCampaign;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeclarationCampaignRepository extends JpaRepository<DeclarationCampaign, UUID> {

    @Query("SELECT x FROM DeclarationCampaign x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<DeclarationCampaign> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM DeclarationCampaign x WHERE x.tenantId = :tenantId AND (:status IS NULL OR x.status = :status) ORDER BY x.createdAt DESC")
    List<DeclarationCampaign> search(TenantId tenantId, CampaignStatus status);

    /** Cross-tenant — the daily reminder sweep. */
    @Query("SELECT x FROM DeclarationCampaign x WHERE x.status = za.co.handyflow.platform.legalcompliance.domain.model.CampaignStatus.OPEN")
    List<DeclarationCampaign> findOpenAcrossTenants();

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}

package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttestationCampaignRepository extends JpaRepository<AttestationCampaign, UUID> {

    @Query("SELECT c FROM AttestationCampaign c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<AttestationCampaign> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT c FROM AttestationCampaign c WHERE c.tenantId = :tenantId AND (:status IS NULL OR c.status = :status)
        ORDER BY c.dueDate DESC, c.reference
        """)
    List<AttestationCampaign> search(TenantId tenantId, CampaignStatus status);

    @Query("SELECT c FROM AttestationCampaign c WHERE c.tenantId = :tenantId AND c.policyId = :policyId AND c.status = za.co.handyflow.platform.legalcompliance.domain.model.CampaignStatus.OPEN")
    List<AttestationCampaign> findOpenForPolicy(TenantId tenantId, UUID policyId);

    @Query("SELECT c FROM AttestationCampaign c WHERE c.tenantId = :tenantId AND c.policyVersionId = :versionId AND c.status = za.co.handyflow.platform.legalcompliance.domain.model.CampaignStatus.OPEN")
    Optional<AttestationCampaign> findOpenForVersion(TenantId tenantId, UUID versionId);

    /** Cross-tenant — daily reminder sweep. */
    @Query("SELECT c FROM AttestationCampaign c WHERE c.status = za.co.handyflow.platform.legalcompliance.domain.model.CampaignStatus.OPEN")
    List<AttestationCampaign> findOpenAcrossTenants();

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}

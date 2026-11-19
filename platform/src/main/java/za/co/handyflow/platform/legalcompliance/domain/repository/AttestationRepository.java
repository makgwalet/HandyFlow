package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttestationRepository extends JpaRepository<Attestation, UUID> {

    @Query("SELECT a FROM Attestation a WHERE a.tenantId = :tenantId AND a.campaignId = :campaignId ORDER BY a.userName")
    List<Attestation> findByCampaign(TenantId tenantId, UUID campaignId);

    @Query("SELECT a FROM Attestation a WHERE a.tenantId = :tenantId AND a.userId = :userId ORDER BY a.createdAt DESC")
    List<Attestation> findForUser(TenantId tenantId, UUID userId);

    @Query("SELECT a FROM Attestation a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<Attestation> findForTenant(TenantId tenantId, UUID id);

    /** Cross-tenant — reminder sweep. */
    @Query("SELECT a FROM Attestation a WHERE a.campaignId IN :campaignIds AND a.status = za.co.handyflow.platform.legalcompliance.domain.model.AttestationStatus.PENDING")
    List<Attestation> findPendingForCampaigns(Collection<UUID> campaignIds);

}

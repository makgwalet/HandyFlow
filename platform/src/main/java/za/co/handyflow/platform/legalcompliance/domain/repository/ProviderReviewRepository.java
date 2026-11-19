package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.ProviderReview;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ProviderReviewRepository extends JpaRepository<ProviderReview, UUID> {

    @Query("SELECT x FROM ProviderReview x WHERE x.tenantId = :tenantId AND x.providerClientId = :clientId ORDER BY x.reviewDate DESC, x.createdAt DESC")
    List<ProviderReview> findForClient(TenantId tenantId, UUID clientId);
}

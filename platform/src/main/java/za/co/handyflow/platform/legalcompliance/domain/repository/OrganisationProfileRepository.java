package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.OrganisationProfile;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface OrganisationProfileRepository extends JpaRepository<OrganisationProfile, UUID> {

    // TenantId is an @Embedded type on AggregateRoot — bind it directly (no SpEL unwrap), as the obligation repository does.
    @Query("SELECT p FROM OrganisationProfile p WHERE p.tenantId = :tenantId")
    Optional<OrganisationProfile> findForTenant(TenantId tenantId);
}

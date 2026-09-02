package za.co.handyflow.platform.collectionsagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollAgencyProfileRepository extends JpaRepository<CollAgencyProfile, UUID> {

    @Query("SELECT p FROM CollAgencyProfile p WHERE p.tenantId = :tenantId")
    Optional<CollAgencyProfile> findByTenantId(@Param("tenantId") UUID tenantId);

    /** Cross-tenant sweep for the firm-registration-expiry notification scheduler. */
    @Query("SELECT p FROM CollAgencyProfile p WHERE p.firmRegistrationExpiryDate IS NOT NULL")
    List<CollAgencyProfile> findAllWithFirmRegistrationAcrossTenants();
}

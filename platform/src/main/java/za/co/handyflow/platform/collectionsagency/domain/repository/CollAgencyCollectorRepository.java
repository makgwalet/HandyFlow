package za.co.handyflow.platform.collectionsagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.collectionsagency.domain.model.CollAgencyCollector;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollAgencyCollectorRepository extends JpaRepository<CollAgencyCollector, UUID> {

    @Query("SELECT c FROM CollAgencyCollector c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL ORDER BY c.fullName ASC")
    List<CollAgencyCollector> findAllActive(@Param("tenantId") UUID tenantId);

    @Query("SELECT c FROM CollAgencyCollector c WHERE c.tenantId = :tenantId AND c.id = :id AND c.deletedAt IS NULL")
    Optional<CollAgencyCollector> findActiveById(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /** Cross-tenant sweep for the individual-registration-expiry notification scheduler. */
    @Query("SELECT c FROM CollAgencyCollector c WHERE c.deletedAt IS NULL AND c.active = true AND c.registrationExpiryDate IS NOT NULL")
    List<CollAgencyCollector> findAllActiveWithRegistrationAcrossTenants();
}

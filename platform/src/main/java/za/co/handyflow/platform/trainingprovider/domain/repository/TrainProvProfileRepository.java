package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvProfileRepository extends JpaRepository<TrainProvProfile, UUID> {

    @Query("SELECT p FROM TrainProvProfile p WHERE p.tenantId = :#{#tenantId.value}")
    Optional<TrainProvProfile> findByTenant(TenantId tenantId);

    /** Cross-tenant sweep for the daily accreditation-expiry scan — same convention as this module's other *AcrossTenants queries. */
    @Query("SELECT p FROM TrainProvProfile p WHERE p.accreditationExpiry IS NOT NULL")
    List<TrainProvProfile> findAllWithAccreditationAcrossTenants();
}

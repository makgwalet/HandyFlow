package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmProfile;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FmProfileRepository extends JpaRepository<FmProfile, UUID> {

    @Query("SELECT p FROM FmProfile p WHERE p.tenantId = :#{#tenantId.value}")
    Optional<FmProfile> findByTenant(@Param("tenantId") TenantId tenantId);
}

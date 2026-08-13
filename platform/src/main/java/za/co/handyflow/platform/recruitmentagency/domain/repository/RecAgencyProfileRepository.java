package za.co.handyflow.platform.recruitmentagency.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.recruitmentagency.domain.model.RecAgencyProfile;

import java.util.Optional;
import java.util.UUID;

public interface RecAgencyProfileRepository extends JpaRepository<RecAgencyProfile, UUID> {

    @Query("SELECT p FROM RecAgencyProfile p WHERE p.tenantId = :tenantId")
    Optional<RecAgencyProfile> findByTenantId(@Param("tenantId") UUID tenantId);
}
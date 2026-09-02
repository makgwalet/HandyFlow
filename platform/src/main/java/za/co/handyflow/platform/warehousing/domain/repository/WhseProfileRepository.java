package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseProfile;

import java.util.Optional;
import java.util.UUID;

public interface WhseProfileRepository extends JpaRepository<WhseProfile, UUID> {

    @Query("SELECT p FROM WhseProfile p WHERE p.tenantId = :tenantId")
    Optional<WhseProfile> findByTenantId(@Param("tenantId") UUID tenantId);
}

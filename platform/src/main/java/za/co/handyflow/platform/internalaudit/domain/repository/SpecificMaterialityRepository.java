package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.SpecificMateriality;

import java.util.List;
import java.util.UUID;

public interface SpecificMaterialityRepository extends JpaRepository<SpecificMateriality, UUID> {

    @Query("SELECT m FROM SpecificMateriality m WHERE m.tenantId = :tenantId AND m.engagementId = :engagementId")
    List<SpecificMateriality> findByEngagement(@Param("tenantId") UUID tenantId, @Param("engagementId") UUID engagementId);
}

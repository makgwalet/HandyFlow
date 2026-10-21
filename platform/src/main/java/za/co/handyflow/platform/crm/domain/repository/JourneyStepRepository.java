package za.co.handyflow.platform.crm.domain.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.JourneyStep;
import za.co.handyflow.platform.shared.TenantId;

public interface JourneyStepRepository extends JpaRepository<JourneyStep, UUID> {
    @Query("SELECT s FROM JourneyStep s WHERE s.tenantId = :tenantId AND s.campaignId = :campaignId ORDER BY s.position")
    List<JourneyStep> findForCampaign(@Param("tenantId") TenantId tenantId, @Param("campaignId") UUID campaignId);

    @Query("SELECT s FROM JourneyStep s WHERE s.tenantId = :tenantId")
    List<JourneyStep> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM JourneyStep s WHERE s.tenantId = :tenantId AND s.campaignId = :campaignId")
    void deleteForCampaign(@Param("tenantId") TenantId tenantId, @Param("campaignId") UUID campaignId);
}

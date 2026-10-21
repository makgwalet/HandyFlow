package za.co.handyflow.platform.crm.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.CampaignMember;
import za.co.handyflow.platform.shared.TenantId;

public interface CampaignMemberRepository extends JpaRepository<CampaignMember, UUID> {
    @Query("SELECT m FROM CampaignMember m WHERE m.tenantId = :tenantId AND m.campaignId = :campaignId ORDER BY m.addedAt, m.id")
    List<CampaignMember> findForCampaign(@Param("tenantId") TenantId tenantId, @Param("campaignId") UUID campaignId);

    @Query("SELECT m FROM CampaignMember m WHERE m.tenantId = :tenantId AND m.campaignId = :campaignId AND m.id = :id")
    Optional<CampaignMember> findOne(@Param("tenantId") TenantId tenantId, @Param("campaignId") UUID campaignId, @Param("id") UUID id);

    @Query("SELECT m FROM CampaignMember m WHERE m.tenantId = :tenantId AND m.campaignId = :campaignId AND m.exitedAt IS NULL ORDER BY m.addedAt, m.id")
    List<CampaignMember> findLive(@Param("tenantId") TenantId tenantId, @Param("campaignId") UUID campaignId);

    /** campaignId, status, count: one row per campaign and status. */
    @Query("SELECT m.campaignId, m.status, COUNT(m) FROM CampaignMember m WHERE m.tenantId = :tenantId GROUP BY m.campaignId, m.status")
    List<Object[]> countByCampaignAndStatus(@Param("tenantId") TenantId tenantId);
}

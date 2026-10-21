package za.co.handyflow.platform.crm.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.Campaign;
import za.co.handyflow.platform.crm.domain.model.CampaignStatus;
import za.co.handyflow.platform.shared.TenantId;

public interface CampaignRepository extends JpaRepository<Campaign, UUID> {
    @Query("SELECT c FROM Campaign c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL ORDER BY c.createdAt DESC, c.id")
    List<Campaign> findAllActive(@Param("tenantId") TenantId tenantId);

    @Query("SELECT c FROM Campaign c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL AND c.id = :id")
    Optional<Campaign> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT c FROM Campaign c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL AND c.status = :status")
    List<Campaign> findActiveByStatus(@Param("tenantId") TenantId tenantId, @Param("status") CampaignStatus status);
}

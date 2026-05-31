package za.co.handyflow.platform.marketing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.marketing.domain.model.MktCampaign;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MktCampaignRepository extends JpaRepository<MktCampaign, UUID> {

    @Query("""
        SELECT c FROM MktCampaign c
        WHERE c.tenantId = :tenantId
        AND c.deletedAt IS NULL
        ORDER BY c.createdAt DESC
        """)
    Page<MktCampaign> findAll(TenantId tenantId, Pageable pageable);

    Optional<MktCampaign> findByIdAndTenantId(UUID id, TenantId tenantId);

    // Find scheduled campaigns ready to send
    @Query("""
        SELECT c FROM MktCampaign c
        WHERE c.status = 'SCHEDULED'
        AND c.scheduledAt <= :now
        AND c.deletedAt IS NULL
        """)
    List<MktCampaign> findScheduledReady(Instant now);
}

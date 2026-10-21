package za.co.handyflow.platform.crm.domain.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.JourneyRun;
import za.co.handyflow.platform.shared.TenantId;

public interface JourneyRunRepository extends JpaRepository<JourneyRun, UUID> {
    @Query("SELECT r FROM JourneyRun r WHERE r.tenantId = :tenantId AND r.campaignId = :campaignId ORDER BY r.ranAt DESC, r.id")
    List<JourneyRun> findRecent(@Param("tenantId") TenantId tenantId, @Param("campaignId") UUID campaignId, Pageable pageable);
}

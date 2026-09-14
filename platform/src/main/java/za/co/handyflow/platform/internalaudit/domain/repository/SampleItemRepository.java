package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.SampleItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SampleItemRepository extends JpaRepository<SampleItem, UUID> {

    @Query("SELECT s FROM SampleItem s WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<SampleItem> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT s FROM SampleItem s WHERE s.tenantId = :tenantId AND s.samplingPlanId = :planId ORDER BY s.selectedAt")
    List<SampleItem> findByPlan(@Param("tenantId") UUID tenantId, @Param("planId") UUID planId);
}

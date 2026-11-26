package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvTimetableOverride;

import java.util.List;
import java.util.UUID;

public interface TrainProvTimetableOverrideRepository extends JpaRepository<TrainProvTimetableOverride, UUID> {

    @Query("SELECT o FROM TrainProvTimetableOverride o WHERE o.tenantId = :#{#tenantId.value} AND o.slotId = :slotId ORDER BY o.approvedAt, o.resourceLabel")
    List<TrainProvTimetableOverride> findForSlot(TenantId tenantId, UUID slotId);

    @Query("SELECT COUNT(o) FROM TrainProvTimetableOverride o WHERE o.tenantId = :#{#tenantId.value} AND o.slotId = :slotId")
    long countForSlot(TenantId tenantId, UUID slotId);
}

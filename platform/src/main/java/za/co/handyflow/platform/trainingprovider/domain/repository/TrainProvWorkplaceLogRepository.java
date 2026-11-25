package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvWorkplaceLog;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvWorkplaceLogRepository extends JpaRepository<TrainProvWorkplaceLog, UUID> {

    @Query("SELECT l FROM TrainProvWorkplaceLog l WHERE l.tenantId = :#{#tenantId.value} AND l.placementId = :placementId ORDER BY l.logDate DESC")
    List<TrainProvWorkplaceLog> forPlacement(TenantId tenantId, UUID placementId);

    @Query("SELECT l FROM TrainProvWorkplaceLog l WHERE l.tenantId = :#{#tenantId.value} AND l.id = :id")
    Optional<TrainProvWorkplaceLog> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(l) > 0 FROM TrainProvWorkplaceLog l WHERE l.tenantId = :#{#tenantId.value} AND l.placementId = :placementId AND l.logDate = :day AND l.status = 'RECORDED'")
    boolean dayTaken(TenantId tenantId, UUID placementId, LocalDate day);
}

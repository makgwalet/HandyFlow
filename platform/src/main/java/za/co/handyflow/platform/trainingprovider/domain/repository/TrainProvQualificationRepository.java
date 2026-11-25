package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvQualification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvQualificationRepository extends JpaRepository<TrainProvQualification, UUID> {

    @Query("SELECT q FROM TrainProvQualification q WHERE q.tenantId = :#{#tenantId.value} AND (:status IS NULL OR q.status = :status) ORDER BY q.title")
    List<TrainProvQualification> findAllForTenant(TenantId tenantId, String status);

    @Query("SELECT q FROM TrainProvQualification q WHERE q.tenantId = :#{#tenantId.value} AND q.id = :id")
    Optional<TrainProvQualification> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(q) > 0 FROM TrainProvQualification q WHERE q.tenantId = :#{#tenantId.value} AND q.status = 'ACTIVE' AND LOWER(q.officialRef) = LOWER(:ref) AND (:excludeId IS NULL OR q.id <> :excludeId)")
    boolean refTaken(TenantId tenantId, String ref, UUID excludeId);
}

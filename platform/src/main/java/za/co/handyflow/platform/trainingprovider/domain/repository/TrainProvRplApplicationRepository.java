package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvRplApplication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvRplApplicationRepository extends JpaRepository<TrainProvRplApplication, UUID> {

    @Query("""
        SELECT a FROM TrainProvRplApplication a WHERE a.tenantId = :#{#tenantId.value}
        AND (CAST(:enrolmentId AS uuid) IS NULL OR a.enrolmentId = :enrolmentId)
        AND (:status IS NULL OR a.status = :status)
        ORDER BY a.rplNo DESC
        """)
    List<TrainProvRplApplication> search(TenantId tenantId, UUID enrolmentId, String status);

    @Query("SELECT a FROM TrainProvRplApplication a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<TrainProvRplApplication> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT COUNT(a) > 0 FROM TrainProvRplApplication a WHERE a.tenantId = :#{#tenantId.value} AND a.enrolmentId = :enrolmentId
        AND a.status <> 'WITHDRAWN' AND ((CAST(:moduleId AS uuid) IS NULL AND a.moduleId IS NULL) OR a.moduleId = :moduleId)
        """)
    boolean liveExists(TenantId tenantId, UUID enrolmentId, UUID moduleId);
}

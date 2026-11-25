package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgramme;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvProgrammeRepository extends JpaRepository<TrainProvProgramme, UUID> {

    @Query("""
        SELECT p FROM TrainProvProgramme p WHERE p.tenantId = :#{#tenantId.value}
        AND (:status IS NULL OR p.status = :status)
        AND (:activity IS NULL OR p.trainingActivity = :activity)
        ORDER BY p.title
        """)
    List<TrainProvProgramme> findAllForTenant(TenantId tenantId, String status, String activity);

    @Query("SELECT p FROM TrainProvProgramme p WHERE p.tenantId = :#{#tenantId.value} AND p.id = :id")
    Optional<TrainProvProgramme> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(p) > 0 FROM TrainProvProgramme p WHERE p.tenantId = :#{#tenantId.value} AND LOWER(p.code) = LOWER(:code) AND (:excludeId IS NULL OR p.id <> :excludeId)")
    boolean codeTaken(TenantId tenantId, String code, UUID excludeId);

    @Query("SELECT COUNT(p) FROM TrainProvProgramme p WHERE p.tenantId = :#{#tenantId.value} AND p.qualificationId = :qualificationId AND p.status <> 'RETIRED'")
    long countLiveForQualification(TenantId tenantId, UUID qualificationId);
}

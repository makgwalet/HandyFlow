package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvQualificationVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvQualificationVersionRepository extends JpaRepository<TrainProvQualificationVersion, UUID> {

    @Query("SELECT v FROM TrainProvQualificationVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.qualificationId = :qualificationId ORDER BY v.versionNo DESC")
    List<TrainProvQualificationVersion> findForQualification(TenantId tenantId, UUID qualificationId);

    @Query("SELECT v FROM TrainProvQualificationVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.id = :id")
    Optional<TrainProvQualificationVersion> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT v FROM TrainProvQualificationVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.qualificationId = :qualificationId AND v.status = 'CURRENT'")
    Optional<TrainProvQualificationVersion> findCurrent(TenantId tenantId, UUID qualificationId);

    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM TrainProvQualificationVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.qualificationId = :qualificationId")
    int maxVersionNo(TenantId tenantId, UUID qualificationId);
}

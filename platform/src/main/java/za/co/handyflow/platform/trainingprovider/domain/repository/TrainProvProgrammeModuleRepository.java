package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgrammeModule;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvProgrammeModuleRepository extends JpaRepository<TrainProvProgrammeModule, UUID> {

    @Query("SELECT m FROM TrainProvProgrammeModule m WHERE m.tenantId = :#{#tenantId.value} AND m.programmeVersionId = :versionId ORDER BY m.seq")
    List<TrainProvProgrammeModule> findForVersion(TenantId tenantId, UUID versionId);

    @Query("SELECT m FROM TrainProvProgrammeModule m WHERE m.tenantId = :#{#tenantId.value} AND m.id = :id")
    Optional<TrainProvProgrammeModule> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT COALESCE(MAX(m.seq), 0) FROM TrainProvProgrammeModule m WHERE m.tenantId = :#{#tenantId.value} AND m.programmeVersionId = :versionId")
    int maxSeq(TenantId tenantId, UUID versionId);
}

package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgrammeVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvProgrammeVersionRepository extends JpaRepository<TrainProvProgrammeVersion, UUID> {

    @Query("SELECT v FROM TrainProvProgrammeVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.programmeId = :programmeId ORDER BY v.versionNo DESC")
    List<TrainProvProgrammeVersion> findForProgramme(TenantId tenantId, UUID programmeId);

    @Query("SELECT v FROM TrainProvProgrammeVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.id = :id")
    Optional<TrainProvProgrammeVersion> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT v FROM TrainProvProgrammeVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.programmeId = :programmeId AND v.status = 'APPROVED'")
    Optional<TrainProvProgrammeVersion> findApproved(TenantId tenantId, UUID programmeId);

    @Query("SELECT COALESCE(MAX(v.versionNo), 0) FROM TrainProvProgrammeVersion v WHERE v.tenantId = :#{#tenantId.value} AND v.programmeId = :programmeId")
    int maxVersionNo(TenantId tenantId, UUID programmeId);
}

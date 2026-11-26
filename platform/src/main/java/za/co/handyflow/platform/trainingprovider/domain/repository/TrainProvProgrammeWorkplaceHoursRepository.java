package za.co.handyflow.platform.trainingprovider.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvProgrammeWorkplaceHours;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainProvProgrammeWorkplaceHoursRepository extends JpaRepository<TrainProvProgrammeWorkplaceHours, UUID> {

    @Query("SELECT h FROM TrainProvProgrammeWorkplaceHours h WHERE h.tenantId = :#{#tenantId.value} AND h.programmeVersionId = :versionId AND h.status = 'ACTIVE'")
    Optional<TrainProvProgrammeWorkplaceHours> findLive(TenantId tenantId, UUID versionId);

    @Query("SELECT h FROM TrainProvProgrammeWorkplaceHours h WHERE h.tenantId = :#{#tenantId.value} AND h.programmeVersionId = :versionId ORDER BY h.createdAt DESC")
    List<TrainProvProgrammeWorkplaceHours> forVersion(TenantId tenantId, UUID versionId);
}

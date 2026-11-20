package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.ResolutionBallot;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResolutionBallotRepository extends JpaRepository<ResolutionBallot, UUID> {

    @Query("SELECT x FROM ResolutionBallot x WHERE x.tenantId = :tenantId AND x.resolutionId = :resolutionId")
    Optional<ResolutionBallot> findForResolution(TenantId tenantId, UUID resolutionId);

    @Query("SELECT x FROM ResolutionBallot x WHERE x.tenantId = :tenantId AND x.meetingId = :meetingId")
    List<ResolutionBallot> findForMeeting(TenantId tenantId, UUID meetingId);
}

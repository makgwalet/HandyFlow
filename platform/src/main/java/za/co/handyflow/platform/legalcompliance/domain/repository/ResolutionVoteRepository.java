package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.ResolutionVote;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface ResolutionVoteRepository extends JpaRepository<ResolutionVote, UUID> {

    @Query("SELECT x FROM ResolutionVote x WHERE x.tenantId = :tenantId AND x.resolutionId = :resolutionId ORDER BY x.memberName")
    List<ResolutionVote> findForResolution(TenantId tenantId, UUID resolutionId);
}

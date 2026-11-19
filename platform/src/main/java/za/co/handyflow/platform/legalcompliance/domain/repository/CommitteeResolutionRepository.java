package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommitteeResolutionRepository extends JpaRepository<CommitteeResolution, UUID> {

    @Query("SELECT x FROM CommitteeResolution x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<CommitteeResolution> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM CommitteeResolution x WHERE x.tenantId = :tenantId AND x.meetingId = :meetingId ORDER BY x.createdAt ASC")
    List<CommitteeResolution> findForMeeting(TenantId tenantId, UUID meetingId);

    @Query("SELECT x FROM CommitteeResolution x WHERE x.tenantId = :tenantId AND x.kind = za.co.handyflow.platform.legalcompliance.domain.model.ResolutionKind.ACTION AND (:open = FALSE OR x.status = za.co.handyflow.platform.legalcompliance.domain.model.ResolutionStatus.OPEN) ORDER BY x.dueDate ASC")
    List<CommitteeResolution> findActions(TenantId tenantId, boolean open);

    @Query("SELECT x FROM CommitteeResolution x WHERE x.tenantId = :tenantId AND x.committeeId IN :ids AND x.kind = za.co.handyflow.platform.legalcompliance.domain.model.ResolutionKind.ACTION AND x.status = za.co.handyflow.platform.legalcompliance.domain.model.ResolutionStatus.OPEN")
    List<CommitteeResolution> findOpenActionsForCommittees(TenantId tenantId, Collection<UUID> ids);

    /** Cross-tenant — daily sweep: open actions due on or before the date. */
    @Query("SELECT x FROM CommitteeResolution x WHERE x.kind = za.co.handyflow.platform.legalcompliance.domain.model.ResolutionKind.ACTION AND x.status = za.co.handyflow.platform.legalcompliance.domain.model.ResolutionStatus.OPEN AND x.dueDate <= :date")
    List<CommitteeResolution> findDueAcrossTenants(LocalDate date);
}

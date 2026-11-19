package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommitteeMeetingRepository extends JpaRepository<CommitteeMeeting, UUID> {

    @Query("SELECT x FROM CommitteeMeeting x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<CommitteeMeeting> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM CommitteeMeeting x WHERE x.tenantId = :tenantId AND x.committeeId = :committeeId ORDER BY x.meetingDate DESC")
    List<CommitteeMeeting> findForCommittee(TenantId tenantId, UUID committeeId);

    @Query("SELECT x FROM CommitteeMeeting x WHERE x.tenantId = :tenantId AND x.committeeId IN :ids")
    List<CommitteeMeeting> findForCommittees(TenantId tenantId, Collection<UUID> ids);
}

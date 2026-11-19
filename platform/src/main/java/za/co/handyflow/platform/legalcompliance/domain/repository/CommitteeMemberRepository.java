package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommitteeMemberRepository extends JpaRepository<CommitteeMember, UUID> {

    @Query("SELECT x FROM CommitteeMember x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<CommitteeMember> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM CommitteeMember x WHERE x.tenantId = :tenantId AND x.committeeId = :committeeId ORDER BY x.endedOn ASC NULLS FIRST, x.memberName ASC")
    List<CommitteeMember> findForCommittee(TenantId tenantId, UUID committeeId);

    @Query("SELECT x FROM CommitteeMember x WHERE x.tenantId = :tenantId AND x.committeeId IN :ids")
    List<CommitteeMember> findForCommittees(TenantId tenantId, Collection<UUID> ids);
}

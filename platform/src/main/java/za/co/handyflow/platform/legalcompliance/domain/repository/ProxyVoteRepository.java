package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.ProxyVote;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProxyVoteRepository extends JpaRepository<ProxyVote, UUID> {

    @Query("SELECT x FROM ProxyVote x WHERE x.tenantId = :tenantId AND x.resolutionId = :resolutionId")
    List<ProxyVote> findForResolution(TenantId tenantId, UUID resolutionId);

    /** Used to refuse revoking a proxy that has already voted. */
    @Query("SELECT x FROM ProxyVote x WHERE x.tenantId = :tenantId AND x.proxyId IN :proxyIds")
    List<ProxyVote> findForProxies(TenantId tenantId, Collection<UUID> proxyIds);
}

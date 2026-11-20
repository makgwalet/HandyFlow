package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.WatchSource;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchSourceRepository extends JpaRepository<WatchSource, UUID> {

    @Query("SELECT x FROM WatchSource x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<WatchSource> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM WatchSource x WHERE x.tenantId = :tenantId ORDER BY x.name")
    List<WatchSource> findAllForTenant(TenantId tenantId);

    @Query("SELECT COUNT(x) > 0 FROM WatchSource x WHERE x.tenantId = :tenantId AND x.feedUrl = :feedUrl")
    boolean existsByUrl(TenantId tenantId, String feedUrl);

    /** Cross-tenant — daily fetch. */
    @Query("SELECT x FROM WatchSource x WHERE x.active = TRUE")
    List<WatchSource> findActiveAcrossTenants();
}

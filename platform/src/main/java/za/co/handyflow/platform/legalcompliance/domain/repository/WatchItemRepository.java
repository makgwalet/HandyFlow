package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.WatchItem;
import za.co.handyflow.platform.legalcompliance.domain.model.WatchItemStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WatchItemRepository extends JpaRepository<WatchItem, UUID> {

    @Query("SELECT x FROM WatchItem x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<WatchItem> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT x FROM WatchItem x WHERE x.tenantId = :tenantId
        AND (:status IS NULL OR x.status = :status)
        AND (:sourceId IS NULL OR x.sourceId = :sourceId)
        ORDER BY x.publishedOn DESC NULLS LAST, x.fetchedAt DESC
        """)
    List<WatchItem> search(TenantId tenantId, WatchItemStatus status, UUID sourceId, Pageable pageable);

    /** Guids already stored for a source, so a feed that republishes old entries adds nothing. */
    @Query("SELECT x.guid FROM WatchItem x WHERE x.tenantId = :tenantId AND x.sourceId = :sourceId AND x.guid IN :guids")
    List<String> existingGuids(TenantId tenantId, UUID sourceId, Collection<String> guids);

    @Query("SELECT COUNT(x) FROM WatchItem x WHERE x.tenantId = :tenantId AND x.status = za.co.handyflow.platform.legalcompliance.domain.model.WatchItemStatus.NEW")
    long countNew(TenantId tenantId);
}

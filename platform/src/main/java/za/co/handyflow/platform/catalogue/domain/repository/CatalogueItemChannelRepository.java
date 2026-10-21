package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueItemChannel;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CatalogueItemChannelRepository extends JpaRepository<CatalogueItemChannel, UUID> {

    @Query("SELECT x FROM CatalogueItemChannel x WHERE x.tenantId = :tenantId AND x.itemId = :itemId")
    List<CatalogueItemChannel> forItem(TenantId tenantId, UUID itemId);

    /** Which of the given items are already on the channel. */
    @Query("SELECT x FROM CatalogueItemChannel x WHERE x.tenantId = :tenantId AND x.channelId = :channelId AND x.itemId IN :itemIds")
    List<CatalogueItemChannel> onChannel(TenantId tenantId, UUID channelId, Collection<UUID> itemIds);

    @Query("SELECT COUNT(x) FROM CatalogueItemChannel x WHERE x.tenantId = :tenantId AND x.channelId = :channelId")
    long countOnChannel(TenantId tenantId, UUID channelId);

    /** Published counts for every channel of the tenant: rows of [channelId, count]. */
    @Query("SELECT x.channelId, COUNT(x) FROM CatalogueItemChannel x WHERE x.tenantId = :tenantId GROUP BY x.channelId")
    List<Object[]> countsByChannel(TenantId tenantId);

    @Query("SELECT x FROM CatalogueItemChannel x WHERE x.tenantId = :tenantId AND x.channelId = :channelId ORDER BY x.publishedAt DESC, x.id")
    List<CatalogueItemChannel> pageOnChannel(TenantId tenantId, UUID channelId, org.springframework.data.domain.Pageable page);

    /** Every item published to at least one channel. */
    @Query("SELECT DISTINCT x.itemId FROM CatalogueItemChannel x WHERE x.tenantId = :tenantId")
    List<UUID> publishedItemIds(TenantId tenantId);
}

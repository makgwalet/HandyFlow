package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CataloguePriceChange;
import za.co.handyflow.platform.catalogue.domain.model.PriceChangeStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CataloguePriceChangeRepository extends JpaRepository<CataloguePriceChange, UUID> {

    @Query("SELECT c FROM CataloguePriceChange c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<CataloguePriceChange> findOwned(TenantId tenantId, UUID id);

    /** History of one item, newest first. */
    @Query("SELECT c FROM CataloguePriceChange c WHERE c.tenantId = :tenantId AND c.itemId = :itemId ORDER BY c.proposedAt DESC")
    List<CataloguePriceChange> history(TenantId tenantId, UUID itemId, Pageable pageable);

    /** Changes still waiting or scheduled for one item. */
    @Query("SELECT c FROM CataloguePriceChange c WHERE c.tenantId = :tenantId AND c.itemId = :itemId AND c.status IN "
            + "(za.co.handyflow.platform.catalogue.domain.model.PriceChangeStatus.PENDING, za.co.handyflow.platform.catalogue.domain.model.PriceChangeStatus.SCHEDULED) "
            + "ORDER BY c.effectiveFrom, c.proposedAt")
    List<CataloguePriceChange> openForItem(TenantId tenantId, UUID itemId);

    /** The approval queue and the scheduled list of the whole tenant. */
    @Query("SELECT c FROM CataloguePriceChange c WHERE c.tenantId = :tenantId AND c.status = :status ORDER BY c.proposedAt")
    List<CataloguePriceChange> byStatus(TenantId tenantId, PriceChangeStatus status, Pageable pageable);

    @Query("SELECT COUNT(c) FROM CataloguePriceChange c WHERE c.tenantId = :tenantId AND c.status = :status")
    long countByStatus(TenantId tenantId, PriceChangeStatus status);

    /** Applied changes of an item, latest first: the first one per list is that list's price. */
    @Query("SELECT c FROM CataloguePriceChange c WHERE c.tenantId = :tenantId AND c.itemId = :itemId AND c.status = "
            + "za.co.handyflow.platform.catalogue.domain.model.PriceChangeStatus.APPLIED ORDER BY c.effectiveFrom DESC, c.appliedAt DESC")
    List<CataloguePriceChange> appliedForItem(TenantId tenantId, UUID itemId);

    /** Scheduled changes whose day has come, across tenants (system job). */
    @Query("SELECT c FROM CataloguePriceChange c WHERE c.status = za.co.handyflow.platform.catalogue.domain.model.PriceChangeStatus.SCHEDULED "
            + "AND c.effectiveFrom <= :today ORDER BY c.effectiveFrom, c.proposedAt")
    List<CataloguePriceChange> due(LocalDate today);

    @Query("SELECT COUNT(c) > 0 FROM CataloguePriceChange c WHERE c.tenantId = :tenantId AND c.itemId = :itemId AND c.priceListId = :listId AND c.status = "
            + "za.co.handyflow.platform.catalogue.domain.model.PriceChangeStatus.PENDING")
    boolean pendingExists(TenantId tenantId, UUID itemId, UUID listId);

    @Query("SELECT COUNT(c) FROM CataloguePriceChange c WHERE c.tenantId = :tenantId AND c.priceListId = :listId")
    long countForList(TenantId tenantId, UUID listId);

    /** Applied changes of several items on one list, latest first: the first one per item is that item's price on the list. */
    @Query("SELECT c FROM CataloguePriceChange c WHERE c.tenantId = :tenantId AND c.priceListId = :listId AND c.itemId IN :itemIds AND c.status = "
            + "za.co.handyflow.platform.catalogue.domain.model.PriceChangeStatus.APPLIED ORDER BY c.effectiveFrom DESC, c.appliedAt DESC")
    List<CataloguePriceChange> appliedOnList(TenantId tenantId, UUID listId, java.util.Collection<UUID> itemIds);
}

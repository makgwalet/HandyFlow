package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GiftEntryRepository extends JpaRepository<GiftEntry, UUID> {

    @Query("SELECT x FROM GiftEntry x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<GiftEntry> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT x FROM GiftEntry x WHERE x.tenantId = :tenantId
        AND (:declarantId IS NULL OR x.declarantId = :declarantId)
        AND (:status IS NULL OR x.status = :status)
        ORDER BY x.entryDate DESC
        """)
    List<GiftEntry> search(TenantId tenantId, UUID declarantId, GiftStatus status, Pageable pageable);

    /** Everything, for summaries, coverage and sweeps that must count every row. */
    default List<GiftEntry> search(TenantId tenantId, UUID declarantId, GiftStatus status) { return search(tenantId, declarantId, status, Pageable.unpaged()); }

    /** In the query's own order, at most RowLimit.MAX_ROWS: for list screens only. */
    default List<GiftEntry> searchCapped(TenantId tenantId, UUID declarantId, GiftStatus status) { return search(tenantId, declarantId, status, RowLimit.first()); }

    /** Cross-tenant — daily sweep: entries awaiting approval. */
    @Query("SELECT x FROM GiftEntry x WHERE x.status = za.co.handyflow.platform.legalcompliance.domain.model.GiftStatus.PENDING")
    List<GiftEntry> findPendingAcrossTenants();

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}

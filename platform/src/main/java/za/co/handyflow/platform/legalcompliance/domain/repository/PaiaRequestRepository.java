package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaiaRequestRepository extends JpaRepository<PaiaRequest, UUID> {

    @Query("SELECT x FROM PaiaRequest x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<PaiaRequest> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT x FROM PaiaRequest x WHERE x.tenantId = :tenantId
        AND (:status IS NULL OR x.status = :status)
        AND (:includeClosed = TRUE OR x.status <> za.co.handyflow.platform.legalcompliance.domain.model.PaiaStatus.CLOSED)
        ORDER BY x.receivedDate DESC
        """)
    List<PaiaRequest> search(TenantId tenantId, PaiaStatus status, boolean includeClosed, Pageable pageable);

    /** Everything, for summaries, coverage and sweeps that must count every row. */
    default List<PaiaRequest> search(TenantId tenantId, PaiaStatus status, boolean includeClosed) { return search(tenantId, status, includeClosed, Pageable.unpaged()); }

    /** In the query's own order, at most RowLimit.MAX_ROWS: for list screens only. */
    default List<PaiaRequest> searchCapped(TenantId tenantId, PaiaStatus status, boolean includeClosed) { return search(tenantId, status, includeClosed, RowLimit.first()); }

    /** Cross-tenant — daily sweep: requests still awaiting a decision. */
    @Query("SELECT x FROM PaiaRequest x WHERE x.status = za.co.handyflow.platform.legalcompliance.domain.model.PaiaStatus.RECEIVED")
    List<PaiaRequest> findAwaitingDecisionAcrossTenants();

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}

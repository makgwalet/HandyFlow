package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskRepository extends JpaRepository<Risk, UUID> {

    @Query("SELECT r FROM Risk r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<Risk> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT r FROM Risk r WHERE r.tenantId = :tenantId
        AND (:status IS NULL OR r.status = :status)
        AND (:category IS NULL OR r.category = :category)
        AND (:includeClosed = TRUE OR r.status <> za.co.handyflow.platform.legalcompliance.domain.model.RiskStatus.CLOSED)
        ORDER BY r.reference
        """)
    List<Risk> search(TenantId tenantId, RiskStatus status, RiskCategory category, boolean includeClosed, Pageable pageable);

    /** Everything, for summaries, coverage and sweeps that must count every row. */
    default List<Risk> search(TenantId tenantId, RiskStatus status, RiskCategory category, boolean includeClosed) { return search(tenantId, status, category, includeClosed, Pageable.unpaged()); }

    /** In the query's own order, at most RowLimit.MAX_ROWS: for list screens only. */
    default List<Risk> searchCapped(TenantId tenantId, RiskStatus status, RiskCategory category, boolean includeClosed) { return search(tenantId, status, category, includeClosed, RowLimit.first()); }

    /** Globally unique, increasing; per-tenant numbering therefore has gaps. */
    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();

    /** Cross-tenant — daily review sweep only. */
    @Query("SELECT r FROM Risk r WHERE r.status <> za.co.handyflow.platform.legalcompliance.domain.model.RiskStatus.CLOSED AND r.nextReviewDate < :today")
    List<Risk> findReviewOverdueAcrossTenants(LocalDate today);

    /** Cross-tenant — daily sweep that lapses acceptances whose expiry date has passed. */
    @Query("SELECT r FROM Risk r WHERE r.status = za.co.handyflow.platform.legalcompliance.domain.model.RiskStatus.ACCEPTED AND r.acceptanceExpires < :today")
    List<Risk> findAcceptanceExpiredAcrossTenants(LocalDate today);
}

package za.co.handyflow.platform.notifications.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.Risk;
import za.co.handyflow.platform.legalcompliance.domain.model.RiskCategory;
import za.co.handyflow.platform.legalcompliance.domain.model.RiskStatus;
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
    List<Risk> search(TenantId tenantId, RiskStatus status, RiskCategory category, boolean includeClosed);

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

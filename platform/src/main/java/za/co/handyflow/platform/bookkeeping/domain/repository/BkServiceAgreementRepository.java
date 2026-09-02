package za.co.handyflow.platform.bookkeeping.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.bookkeeping.domain.model.BkServiceAgreement;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link BkServiceAgreement} has no {@code deletedAt} — only ever ended, never deleted. Direct mirror of {@code FmServiceAgreementRepository}. */
public interface BkServiceAgreementRepository extends JpaRepository<BkServiceAgreement, UUID> {

    @Query("SELECT a FROM BkServiceAgreement a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<BkServiceAgreement> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT a FROM BkServiceAgreement a WHERE a.tenantId = :#{#tenantId.value} AND a.clientId = :clientId ORDER BY a.startDate DESC")
    Page<BkServiceAgreement> findAllForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT a FROM BkServiceAgreement a WHERE a.tenantId = :#{#tenantId.value} AND a.clientId = :clientId ORDER BY a.startDate DESC")
    List<BkServiceAgreement> findAllForClientList(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    /**
     * The client's agreement in force on a given date — used by {@code
     * BkBillingService} to decide RETAINER vs. time-and-materials for a
     * billing period. At most one row is expected to satisfy this per
     * client at a time; the query itself doesn't enforce that uniqueness.
     */
    @Query("SELECT a FROM BkServiceAgreement a WHERE a.tenantId = :#{#tenantId.value} AND a.clientId = :clientId " +
           "AND a.status = 'ACTIVE' AND a.startDate <= :asOfDate AND (a.endDate IS NULL OR a.endDate >= :asOfDate) " +
           "ORDER BY a.startDate DESC")
    List<BkServiceAgreement> findActiveAsOfDate(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                                 @Param("asOfDate") LocalDate asOfDate);

    /** Cross-tenant sweep: ACTIVE agreements with an end date approaching, for the daily "agreement expiring" alert. */
    @Query("SELECT a FROM BkServiceAgreement a WHERE a.status = 'ACTIVE' AND a.endDate IS NOT NULL AND a.endDate <= :cutoff AND a.endDate >= :today")
    List<BkServiceAgreement> findExpiringAcrossTenants(@Param("today") LocalDate today, @Param("cutoff") LocalDate cutoff);
}

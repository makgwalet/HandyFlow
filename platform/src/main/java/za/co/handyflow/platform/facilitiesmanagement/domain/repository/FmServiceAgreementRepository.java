package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmServiceAgreement;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FmServiceAgreementRepository extends JpaRepository<FmServiceAgreement, UUID> {

    @Query("SELECT a FROM FmServiceAgreement a WHERE a.tenantId = :#{#tenantId.value} AND a.id = :id")
    Optional<FmServiceAgreement> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT a FROM FmServiceAgreement a WHERE a.tenantId = :#{#tenantId.value} AND a.clientId = :clientId ORDER BY a.startDate DESC")
    Page<FmServiceAgreement> findAllActiveForClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    @Query("SELECT a FROM FmServiceAgreement a WHERE a.tenantId = :#{#tenantId.value} AND a.clientId = :clientId ORDER BY a.startDate DESC")
    List<FmServiceAgreement> findAllForClientList(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId);

    /**
     * The client's agreement in force on a given date — used by
     * {@code FmBillingService} to decide RETAINER vs. time-and-materials
     * for a billing period. status='ACTIVE' and startDate/endDate bracket
     * asOfDate (endDate NULL means open-ended). At most one row is
     * expected to satisfy this per client at a time, but the query itself
     * doesn't enforce that uniqueness — the service takes the first match.
     */
    @Query("SELECT a FROM FmServiceAgreement a WHERE a.tenantId = :#{#tenantId.value} AND a.clientId = :clientId " +
           "AND a.status = 'ACTIVE' AND a.startDate <= :asOfDate AND (a.endDate IS NULL OR a.endDate >= :asOfDate) " +
           "ORDER BY a.startDate DESC")
    List<FmServiceAgreement> findActiveAsOfDate(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                                 @Param("asOfDate") LocalDate asOfDate);

    /** Cross-tenant sweep: ACTIVE agreements with an end date approaching, for the daily "agreement expiring" alert. */
    @Query("SELECT a FROM FmServiceAgreement a WHERE a.status = 'ACTIVE' AND a.endDate IS NOT NULL AND a.endDate <= :cutoff AND a.endDate >= :today")
    List<FmServiceAgreement> findExpiringAcrossTenants(@Param("today") LocalDate today, @Param("cutoff") LocalDate cutoff);
}

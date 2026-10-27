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
     * The client's ACTIVE agreements in force on a given date (endDate NULL means open-ended). A client may now
     * have several; callers narrow by site and category (see {@code FmContractTermsService}).
     */
    @Query("SELECT a FROM FmServiceAgreement a WHERE a.tenantId = :#{#tenantId.value} AND a.clientId = :clientId " +
            "AND a.status = 'ACTIVE' AND a.startDate <= :asOfDate AND (a.endDate IS NULL OR a.endDate >= :asOfDate) " +
            "ORDER BY a.startDate DESC")
    List<FmServiceAgreement> findActiveAsOfDate(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                                @Param("asOfDate") LocalDate asOfDate);

    /**
     * The client's other ACTIVE agreements whose dates overlap [start, end] — used to reject a new or changed
     * agreement that would cover the same sites and categories at the same time. {@code end} must not be null
     * (pass a far-future date for an open-ended agreement); {@code excludeId} excludes the agreement being edited.
     */
    @Query("SELECT a FROM FmServiceAgreement a WHERE a.tenantId = :#{#tenantId.value} AND a.clientId = :clientId " +
            "AND a.status = 'ACTIVE' AND a.id <> :excludeId AND a.startDate <= :end " +
            "AND (a.endDate IS NULL OR a.endDate >= :start)")
    List<FmServiceAgreement> findActiveOverlapping(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                                   @Param("start") LocalDate start, @Param("end") LocalDate end,
                                                   @Param("excludeId") UUID excludeId);

    /** Cross-tenant sweep: ACTIVE agreements with an end date approaching, for the daily "agreement expiring" alert. */
    @Query("SELECT a FROM FmServiceAgreement a WHERE a.status = 'ACTIVE' AND a.endDate IS NOT NULL AND a.endDate <= :cutoff AND a.endDate >= :today")
    List<FmServiceAgreement> findExpiringAcrossTenants(@Param("today") LocalDate today, @Param("cutoff") LocalDate cutoff);
}
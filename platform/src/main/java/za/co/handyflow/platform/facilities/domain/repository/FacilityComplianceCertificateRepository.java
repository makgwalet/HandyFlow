package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityComplianceCertificate;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityComplianceCertificateRepository extends JpaRepository<FacilityComplianceCertificate, UUID> {

    @Query("SELECT c FROM FacilityComplianceCertificate c WHERE c.tenantId = :#{#tenantId.value} AND c.id = :id")
    Optional<FacilityComplianceCertificate> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT c FROM FacilityComplianceCertificate c WHERE c.tenantId = :#{#tenantId.value} " +
            "AND (:siteId IS NULL OR c.siteId = :siteId) ORDER BY c.expiryDate ASC")
    Page<FacilityComplianceCertificate> findAll(@Param("tenantId") TenantId tenantId, @Param("siteId") UUID siteId, Pageable pageable);

    /** Scope-aware list. {@code siteIds} must never be empty. */
    @Query("SELECT c FROM FacilityComplianceCertificate c WHERE c.tenantId = :#{#tenantId.value} " +
            "AND (:siteId IS NULL OR c.siteId = :siteId) " +
            "AND (:allSites = true OR c.siteId IN :siteIds) ORDER BY c.expiryDate ASC")
    Page<FacilityComplianceCertificate> findAllInScope(@Param("tenantId") TenantId tenantId, @Param("siteId") UUID siteId,
                                                       @Param("allSites") boolean allSites,
                                                       @Param("siteIds") Collection<UUID> siteIds, Pageable pageable);

    /** Cross-tenant sweep: every certificate still VALID with an expiry date to check (used for both the expiring-soon and just-expired alerts). */
    @Query("SELECT c FROM FacilityComplianceCertificate c WHERE c.status = 'VALID' AND c.expiryDate <= :cutoff")
    List<FacilityComplianceCertificate> findAllValidWithExpiryAcrossTenants(@Param("cutoff") LocalDate cutoff);


    /** VALID certificates expiring from {@code today} up to and including {@code cutoff}, within site scope. */
    @Query("SELECT COUNT(c) FROM FacilityComplianceCertificate c WHERE c.tenantId = :#{#tenantId.value} AND c.status = 'VALID' " +
            "AND c.expiryDate >= :today AND c.expiryDate <= :cutoff AND (:allSites = true OR c.siteId IN :siteIds)")
    long countExpiringInScope(@Param("tenantId") TenantId tenantId, @Param("today") LocalDate today, @Param("cutoff") LocalDate cutoff,
                              @Param("allSites") boolean allSites, @Param("siteIds") Collection<UUID> siteIds);

    /** Certificates past their expiry date (swept to EXPIRED or not yet swept) that were not revoked, within site scope. */
    @Query("SELECT COUNT(c) FROM FacilityComplianceCertificate c WHERE c.tenantId = :#{#tenantId.value} AND c.status <> 'REVOKED' " +
            "AND c.expiryDate < :today AND (:allSites = true OR c.siteId IN :siteIds)")
    long countLapsedInScope(@Param("tenantId") TenantId tenantId, @Param("today") LocalDate today,
                            @Param("allSites") boolean allSites, @Param("siteIds") Collection<UUID> siteIds);
}

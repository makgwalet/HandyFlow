package za.co.handyflow.platform.facilities.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendorDocument;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityVendorDocumentRepository extends JpaRepository<FacilityVendorDocument, UUID> {

    @Query("SELECT d FROM FacilityVendorDocument d WHERE d.tenantId.value = :#{#tenantId.value} AND d.id = :id")
    Optional<FacilityVendorDocument> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT d FROM FacilityVendorDocument d WHERE d.tenantId.value = :#{#tenantId.value} AND d.vendorId = :vendorId " +
            "ORDER BY d.docType ASC, d.expiryDate DESC")
    List<FacilityVendorDocument> findForVendor(@Param("tenantId") TenantId tenantId, @Param("vendorId") UUID vendorId);

    /** In-force-or-not VALID documents of active, undeleted vendors that have lapsed or expire by {@code cutoff}. */
    @Query("SELECT d FROM FacilityVendorDocument d WHERE d.tenantId.value = :#{#tenantId.value} AND d.status = 'VALID' " +
            "AND d.expiryDate IS NOT NULL AND d.expiryDate <= :cutoff AND d.vendorId IN " +
            "(SELECT v.id FROM FacilityVendor v WHERE v.tenantId.value = :#{#tenantId.value} AND v.active = true AND v.deletedAt IS NULL) " +
            "ORDER BY d.expiryDate ASC")
    List<FacilityVendorDocument> findLapsedOrExpiringForActiveVendors(@Param("tenantId") TenantId tenantId, @Param("cutoff") LocalDate cutoff);

    /** Cross-tenant sweep for the daily reminders: VALID documents of active vendors that expire by {@code cutoff}. */
    @Query("SELECT d FROM FacilityVendorDocument d WHERE d.status = 'VALID' AND d.expiryDate IS NOT NULL AND d.expiryDate <= :cutoff " +
            "AND d.vendorId IN (SELECT v.id FROM FacilityVendor v WHERE v.active = true AND v.deletedAt IS NULL)")
    List<FacilityVendorDocument> findValidExpiringAcrossTenants(@Param("cutoff") LocalDate cutoff);

    @Query("SELECT d FROM FacilityVendorDocument d WHERE d.tenantId.value = :#{#tenantId.value} AND d.vendorId IN :vendorIds")
    List<FacilityVendorDocument> findForVendors(@Param("tenantId") TenantId tenantId, @Param("vendorIds") Collection<UUID> vendorIds);
}

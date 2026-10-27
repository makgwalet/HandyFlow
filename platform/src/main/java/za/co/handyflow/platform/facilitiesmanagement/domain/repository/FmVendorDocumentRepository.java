package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendorDocument;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FmVendorDocumentRepository extends JpaRepository<FmVendorDocument, UUID> {

    @Query("SELECT d FROM FmVendorDocument d WHERE d.tenantId.value = :#{#tenantId.value} AND d.id = :id")
    Optional<FmVendorDocument> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT d FROM FmVendorDocument d WHERE d.tenantId.value = :#{#tenantId.value} AND d.vendorId = :vendorId " +
            "ORDER BY d.docType ASC, d.expiryDate DESC")
    List<FmVendorDocument> findForVendor(@Param("tenantId") TenantId tenantId, @Param("vendorId") UUID vendorId);

    /** In-force-or-not VALID documents of active, undeleted vendors that have lapsed or expire by {@code cutoff}. */
    @Query("SELECT d FROM FmVendorDocument d WHERE d.tenantId.value = :#{#tenantId.value} AND d.status = 'VALID' " +
            "AND d.expiryDate IS NOT NULL AND d.expiryDate <= :cutoff AND d.vendorId IN " +
            "(SELECT v.id FROM FmVendor v WHERE v.tenantId.value = :#{#tenantId.value} AND v.active = true AND v.deletedAt IS NULL) " +
            "ORDER BY d.expiryDate ASC")
    List<FmVendorDocument> findLapsedOrExpiringForActiveVendors(@Param("tenantId") TenantId tenantId, @Param("cutoff") LocalDate cutoff);

    /** Cross-tenant sweep for the daily reminders: VALID documents of active vendors that expire by {@code cutoff}. */
    @Query("SELECT d FROM FmVendorDocument d WHERE d.status = 'VALID' AND d.expiryDate IS NOT NULL AND d.expiryDate <= :cutoff " +
            "AND d.vendorId IN (SELECT v.id FROM FmVendor v WHERE v.active = true AND v.deletedAt IS NULL)")
    List<FmVendorDocument> findValidExpiringAcrossTenants(@Param("cutoff") LocalDate cutoff);

    @Query("SELECT d FROM FmVendorDocument d WHERE d.tenantId.value = :#{#tenantId.value} AND d.vendorId IN :vendorIds")
    List<FmVendorDocument> findForVendors(@Param("tenantId") TenantId tenantId, @Param("vendorIds") Collection<UUID> vendorIds);
}

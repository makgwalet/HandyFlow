package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthmovingIncident;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface EarthmovingIncidentRepository extends JpaRepository<EarthmovingIncident, UUID> {

    @Query("SELECT i FROM EarthmovingIncident i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<EarthmovingIncident> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT i FROM EarthmovingIncident i WHERE i.tenantId = :tenantId ORDER BY i.reportedAt DESC")
    Page<EarthmovingIncident> findAll(TenantId tenantId, Pageable pageable);

    @Query("SELECT i FROM EarthmovingIncident i WHERE i.tenantId = :tenantId AND i.status = :status ORDER BY i.reportedAt DESC")
    Page<EarthmovingIncident> findByStatus(TenantId tenantId, String status, Pageable pageable);

    @Query("SELECT i FROM EarthmovingIncident i WHERE i.tenantId = :tenantId AND i.severity = :severity ORDER BY i.reportedAt DESC")
    Page<EarthmovingIncident> findBySeverity(TenantId tenantId, String severity, Pageable pageable);

    @Query("SELECT i FROM EarthmovingIncident i WHERE i.tenantId = :tenantId AND i.assetId = :assetId ORDER BY i.reportedAt DESC")
    Page<EarthmovingIncident> findByAsset(TenantId tenantId, UUID assetId, Pageable pageable);
}
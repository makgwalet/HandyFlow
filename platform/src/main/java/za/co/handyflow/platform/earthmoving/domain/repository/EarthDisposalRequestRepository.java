package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthDisposalRequest;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EarthDisposalRequestRepository extends JpaRepository<EarthDisposalRequest, UUID> {

    @Query("SELECT r FROM EarthDisposalRequest r WHERE r.tenantId = :tenantId AND r.id = :id")
    Optional<EarthDisposalRequest> findByTenantAndId(TenantId tenantId, UUID id);

    /** Newest first; a null status means all. */
    @Query("SELECT r FROM EarthDisposalRequest r WHERE r.tenantId = :tenantId AND (:status IS NULL OR r.status = :status) ORDER BY r.requestedAt DESC")
    List<EarthDisposalRequest> findForTenant(TenantId tenantId, String status);

    @Query("SELECT r FROM EarthDisposalRequest r WHERE r.tenantId = :tenantId AND r.assetId = :assetId ORDER BY r.requestedAt DESC")
    List<EarthDisposalRequest> findByAsset(TenantId tenantId, UUID assetId);

    @Query("SELECT r FROM EarthDisposalRequest r WHERE r.tenantId = :tenantId AND r.assetId = :assetId AND r.status IN ('REQUESTED', 'APPROVED')")
    Optional<EarthDisposalRequest> findLiveForAsset(TenantId tenantId, UUID assetId);
}

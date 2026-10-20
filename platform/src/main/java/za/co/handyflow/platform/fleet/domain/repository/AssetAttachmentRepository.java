package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.AssetAttachment;
import za.co.handyflow.platform.fleet.domain.model.AttachmentStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetAttachmentRepository extends JpaRepository<AssetAttachment, UUID> {

    @Query("SELECT a FROM AssetAttachment a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<AssetAttachment> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT a FROM AssetAttachment a WHERE a.tenantId = :tenantId ORDER BY a.name")
    Page<AssetAttachment> findAllForTenant(TenantId tenantId, Pageable pageable);

    @Query("SELECT a FROM AssetAttachment a WHERE a.tenantId = :tenantId AND a.status = :status ORDER BY a.name")
    Page<AssetAttachment> findByStatus(TenantId tenantId, AttachmentStatus status, Pageable pageable);

    /** What is currently hitched to this machine. */
    @Query("SELECT a FROM AssetAttachment a WHERE a.tenantId = :tenantId AND a.currentVehicleId = :vehicleId " +
            "AND a.status = za.co.handyflow.platform.fleet.domain.model.AttachmentStatus.ATTACHED ORDER BY a.name")
    List<AssetAttachment> findAttachedToVehicle(TenantId tenantId, UUID vehicleId);

    @Query("SELECT COUNT(a) > 0 FROM AssetAttachment a WHERE a.tenantId = :tenantId " +
            "AND LOWER(a.serialNumber) = LOWER(:serial) AND a.id <> :excludeId")
    boolean existsOtherWithSerial(TenantId tenantId, String serial, UUID excludeId);
}
package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.VehicleDocument;
import za.co.handyflow.platform.fleet.domain.model.VehicleDocumentStatus;
import za.co.handyflow.platform.fleet.domain.model.VehicleDocumentType;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleDocumentRepository extends JpaRepository<VehicleDocument, UUID> {

    /** Every live document for a vehicle, including superseded history. Newest first. */
    @Query("SELECT d FROM VehicleDocument d WHERE d.tenantId = :tenantId AND d.vehicleId = :vehicleId " +
            "AND d.deletedAt IS NULL ORDER BY d.uploadedAt DESC")
    List<VehicleDocument> findAllByVehicle(TenantId tenantId, UUID vehicleId);

    @Query("SELECT d FROM VehicleDocument d WHERE d.tenantId = :tenantId AND d.vehicleId = :vehicleId " +
            "AND d.status = :status AND d.deletedAt IS NULL ORDER BY d.uploadedAt DESC")
    List<VehicleDocument> findByVehicleAndStatus(TenantId tenantId, UUID vehicleId, VehicleDocumentStatus status);

    @Query("SELECT d FROM VehicleDocument d WHERE d.tenantId = :tenantId AND d.vehicleId = :vehicleId " +
            "AND d.type = :type AND d.status = :status AND d.deletedAt IS NULL ORDER BY d.uploadedAt DESC")
    List<VehicleDocument> findByVehicleTypeAndStatus(TenantId tenantId, UUID vehicleId,
                                                     VehicleDocumentType type, VehicleDocumentStatus status);

    /** Highest version ever used for this (vehicle, type), including deleted rows, so versions never repeat. */
    @Query("SELECT COALESCE(MAX(d.documentVersion), 0) FROM VehicleDocument d " +
            "WHERE d.tenantId = :tenantId AND d.vehicleId = :vehicleId AND d.type = :type")
    int findMaxVersion(TenantId tenantId, UUID vehicleId, VehicleDocumentType type);

    @Query("SELECT d FROM VehicleDocument d WHERE d.tenantId = :tenantId AND d.vehicleId = :vehicleId " +
            "AND d.id = :id AND d.deletedAt IS NULL")
    Optional<VehicleDocument> findActiveById(TenantId tenantId, UUID vehicleId, UUID id);
}
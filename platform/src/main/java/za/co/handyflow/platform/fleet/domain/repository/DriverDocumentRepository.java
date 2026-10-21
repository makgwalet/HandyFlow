package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.DriverDocument;
import za.co.handyflow.platform.fleet.domain.model.DriverDocumentType;
import za.co.handyflow.platform.fleet.domain.model.VehicleDocumentStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverDocumentRepository extends JpaRepository<DriverDocument, UUID> {

    /** Every live document for a driver, including superseded history. Newest first. */
    @Query("SELECT d FROM DriverDocument d WHERE d.tenantId = :tenantId AND d.driverId = :driverId " +
            "AND d.deletedAt IS NULL ORDER BY d.uploadedAt DESC")
    List<DriverDocument> findAllByDriver(TenantId tenantId, UUID driverId);

    @Query("SELECT d FROM DriverDocument d WHERE d.tenantId = :tenantId AND d.driverId = :driverId " +
            "AND d.status = :status AND d.deletedAt IS NULL ORDER BY d.uploadedAt DESC")
    List<DriverDocument> findByDriverAndStatus(TenantId tenantId, UUID driverId, VehicleDocumentStatus status);

    @Query("SELECT d FROM DriverDocument d WHERE d.tenantId = :tenantId AND d.driverId = :driverId " +
            "AND d.type = :type AND d.status = :status AND d.deletedAt IS NULL ORDER BY d.uploadedAt DESC")
    List<DriverDocument> findByDriverTypeAndStatus(TenantId tenantId, UUID driverId,
                                                   DriverDocumentType type, VehicleDocumentStatus status);

    /** Highest version ever used for this (driver, type), including deleted rows, so versions never repeat. */
    @Query("SELECT COALESCE(MAX(d.documentVersion), 0) FROM DriverDocument d " +
            "WHERE d.tenantId = :tenantId AND d.driverId = :driverId AND d.type = :type")
    int findMaxVersion(TenantId tenantId, UUID driverId, DriverDocumentType type);

    @Query("SELECT d FROM DriverDocument d WHERE d.tenantId = :tenantId AND d.driverId = :driverId " +
            "AND d.id = :id AND d.deletedAt IS NULL")
    Optional<DriverDocument> findActiveById(TenantId tenantId, UUID driverId, UUID id);
}

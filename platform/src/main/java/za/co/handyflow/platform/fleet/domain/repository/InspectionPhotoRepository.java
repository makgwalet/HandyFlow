package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.InspectionPhoto;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionPhotoRepository extends JpaRepository<InspectionPhoto, UUID> {

    @Query("SELECT p FROM InspectionPhoto p WHERE p.tenantId = :tenantId AND p.inspectionId = :inspectionId ORDER BY p.uploadedAt")
    List<InspectionPhoto> findByInspection(TenantId tenantId, UUID inspectionId);

    @Query("SELECT p FROM InspectionPhoto p WHERE p.tenantId = :tenantId AND p.inspectionId = :inspectionId AND p.id = :id")
    Optional<InspectionPhoto> findByTenantAndId(TenantId tenantId, UUID inspectionId, UUID id);

    @Query("SELECT COUNT(p) FROM InspectionPhoto p WHERE p.tenantId = :tenantId AND p.inspectionId = :inspectionId")
    long countByInspection(TenantId tenantId, UUID inspectionId);
}
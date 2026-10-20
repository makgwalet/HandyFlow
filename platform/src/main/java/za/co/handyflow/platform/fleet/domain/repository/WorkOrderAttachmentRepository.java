package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.WorkOrderAttachment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkOrderAttachmentRepository extends JpaRepository<WorkOrderAttachment, UUID> {

    @Query("SELECT a FROM WorkOrderAttachment a WHERE a.tenantId = :tenantId AND a.workOrderId = :workOrderId " +
            "AND a.deletedAt IS NULL ORDER BY a.uploadedAt")
    List<WorkOrderAttachment> findActiveByWorkOrder(TenantId tenantId, UUID workOrderId);

    @Query("SELECT a FROM WorkOrderAttachment a WHERE a.tenantId = :tenantId AND a.workOrderId = :workOrderId " +
            "AND a.id = :id AND a.deletedAt IS NULL")
    Optional<WorkOrderAttachment> findActiveById(TenantId tenantId, UUID workOrderId, UUID id);

    @Query("SELECT COUNT(a) FROM WorkOrderAttachment a WHERE a.tenantId = :tenantId AND a.workOrderId = :workOrderId " +
            "AND a.deletedAt IS NULL")
    long countActive(TenantId tenantId, UUID workOrderId);
}
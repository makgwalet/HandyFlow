package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.AttachmentAssignment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentAssignmentRepository extends JpaRepository<AttachmentAssignment, UUID> {

    @Query("SELECT a FROM AttachmentAssignment a WHERE a.tenantId = :tenantId AND a.attachmentId = :attachmentId " +
            "ORDER BY a.attachedAt DESC")
    List<AttachmentAssignment> findByAttachment(TenantId tenantId, UUID attachmentId);

    @Query("SELECT a FROM AttachmentAssignment a WHERE a.tenantId = :tenantId AND a.attachmentId = :attachmentId " +
            "AND a.detachedAt IS NULL")
    Optional<AttachmentAssignment> findOpenByAttachment(TenantId tenantId, UUID attachmentId);
}
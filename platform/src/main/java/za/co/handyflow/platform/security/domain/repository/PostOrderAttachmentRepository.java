package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.PostOrderAttachment;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface PostOrderAttachmentRepository extends JpaRepository<PostOrderAttachment, UUID> {

    @Query("SELECT a FROM PostOrderAttachment a WHERE a.tenantId = :tenantId AND a.postOrderId = :postOrderId")
    List<PostOrderAttachment> findByPostOrder(TenantId tenantId, UUID postOrderId);
}

package za.co.handyflow.platform.admin.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.admin.domain.model.AdminAuditLog;

import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {
    Page<AdminAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<AdminAuditLog> findByTargetIdOrderByCreatedAtDesc(String targetId, Pageable pageable);
}

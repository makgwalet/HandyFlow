package za.co.handyflow.platform.admin.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.admin.domain.model.AdminImpersonationSession;

import java.util.List;
import java.util.UUID;

public interface AdminImpersonationSessionRepository
        extends JpaRepository<AdminImpersonationSession, UUID> {
    List<AdminImpersonationSession> findByAdminUserIdOrderByStartedAtDesc(UUID adminUserId);
    List<AdminImpersonationSession> findByTenantIdOrderByStartedAtDesc(UUID tenantId);
}

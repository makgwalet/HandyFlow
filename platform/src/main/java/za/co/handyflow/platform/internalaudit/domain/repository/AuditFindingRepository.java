package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditFinding;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditFindingRepository extends JpaRepository<AuditFinding, UUID> {

    @Query("SELECT f FROM AuditFinding f WHERE f.tenantId = :tenantId AND f.id = :id")
    Optional<AuditFinding> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT f FROM AuditFinding f WHERE f.tenantId = :tenantId AND f.engagementId = :engagementId ORDER BY f.createdAt DESC")
    List<AuditFinding> findByEngagement(@Param("tenantId") UUID tenantId, @Param("engagementId") UUID engagementId);
}

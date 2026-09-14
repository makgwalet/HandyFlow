package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditEngagement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditEngagementRepository extends JpaRepository<AuditEngagement, UUID> {

    @Query("SELECT e FROM AuditEngagement e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<AuditEngagement> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT e FROM AuditEngagement e WHERE e.tenantId = :tenantId ORDER BY e.createdAt DESC")
    List<AuditEngagement> findAllForTenant(@Param("tenantId") UUID tenantId);
}

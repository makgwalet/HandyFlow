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

    // FIX: closes the confirmed "no internal-audit engine reachable by
    // an external auditor" gap — backs the auditor portal's own
    // curated read. externalVisibility = 'SHARED' specifically (not
    // != 'INTERNAL_ONLY') so a WITHDRAWN finding correctly disappears
    // from the auditor's own view the moment it's withdrawn, not just
    // stops accepting new shares.
    @Query("SELECT f FROM AuditFinding f WHERE f.tenantId = :tenantId AND f.externalVisibility = 'SHARED' ORDER BY f.sharedAt DESC")
    List<AuditFinding> findSharedForTenant(@Param("tenantId") UUID tenantId);
}

package za.co.handyflow.platform.internalaudit.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.internalaudit.domain.model.AuditReportFactItem;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditReportFactItemRepository extends JpaRepository<AuditReportFactItem, UUID> {
    List<AuditReportFactItem> findByTenantIdAndReportIdOrderByRaisedAt(UUID tenantId, UUID reportId);
    Optional<AuditReportFactItem> findByTenantIdAndId(UUID tenantId, UUID id);
    long countByTenantIdAndReportIdAndStatus(UUID tenantId, UUID reportId, String status);

    /** Cross-tenant sweep for reminders: comments still waiting for an answer since before the cut-off. */
    @Query("SELECT i FROM AuditReportFactItem i WHERE i.status = 'OPEN' AND i.raisedAt < :before")
    List<AuditReportFactItem> findOpenRaisedBefore(@Param("before") Instant before);
}

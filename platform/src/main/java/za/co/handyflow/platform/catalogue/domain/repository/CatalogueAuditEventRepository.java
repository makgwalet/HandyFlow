package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueAuditEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CatalogueAuditEventRepository extends JpaRepository<CatalogueAuditEvent, UUID> {

    @Query("SELECT e FROM CatalogueAuditEvent e WHERE e.tenantId = :tenantId AND e.entityId = :entityId ORDER BY e.createdAt DESC")
    Page<CatalogueAuditEvent> history(TenantId tenantId, UUID entityId, Pageable pageable);

    /** The latest events of the whole tenant, newest first. */
    @Query("SELECT e FROM CatalogueAuditEvent e WHERE e.tenantId = :tenantId ORDER BY e.createdAt DESC")
    List<CatalogueAuditEvent> recent(TenantId tenantId, Pageable pageable);

    /** Item history lines after a change number, oldest first, settled for a few seconds so a still-open save is not skipped. */
    @Query("SELECT e FROM CatalogueAuditEvent e WHERE e.tenantId = :tenantId AND e.entityType = 'ITEM' AND e.seq > :since AND e.createdAt <= :settledBefore ORDER BY e.seq")
    List<CatalogueAuditEvent> itemChangesAfter(TenantId tenantId, long since, Instant settledBefore, Pageable page);

    /** Item history lines since a moment, newest first (for the "recently changed" report). */
    @Query("SELECT e FROM CatalogueAuditEvent e WHERE e.tenantId = :tenantId AND e.entityType = 'ITEM' AND e.createdAt >= :since ORDER BY e.createdAt DESC")
    List<CatalogueAuditEvent> itemLinesSince(TenantId tenantId, Instant since, Pageable page);
}

// security/domain/repository/AuditEventRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.AuditEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    @Query("""
        SELECT e FROM AuditEvent e
        WHERE e.tenantId = :tenantId
        AND e.entityType = :entityType
        AND e.entityId = :entityId
        ORDER BY e.occurredAt DESC
        """)
    Page<AuditEvent> findByEntity(TenantId tenantId, String entityType, UUID entityId,
                                  Pageable pageable);

    @Query("""
        SELECT e FROM AuditEvent e
        WHERE e.tenantId = :tenantId
        AND e.actorId = :actorId
        ORDER BY e.occurredAt DESC
        """)
    Page<AuditEvent> findByActor(TenantId tenantId, UUID actorId, Pageable pageable);

    @Query("""
        SELECT e FROM AuditEvent e
        WHERE e.tenantId = :tenantId
        AND e.entityType = :entityType
        AND e.entityId = :entityId
        AND e.action = 'VIEWED'
        ORDER BY e.occurredAt DESC
        """)
    Page<AuditEvent> findViewHistory(TenantId tenantId, String entityType, UUID entityId,
                                     Pageable pageable);
}

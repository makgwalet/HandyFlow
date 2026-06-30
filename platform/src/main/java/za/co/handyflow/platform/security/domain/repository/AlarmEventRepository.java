// security/domain/repository/AlarmEventRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.AlarmEvent;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlarmEventRepository extends JpaRepository<AlarmEvent, UUID> {

    @Query("""
        SELECT e FROM AlarmEvent e
        WHERE e.tenantId = :tenantId
        AND e.id = :id
        """)
    Optional<AlarmEvent> findByTenantAndId(TenantId tenantId, UUID id);

    /** The triage queue — events not yet resolved or dismissed, newest first. */
    @Query("""
        SELECT e FROM AlarmEvent e
        WHERE e.tenantId = :tenantId
        AND e.status IN ('NEW', 'TRIAGED', 'DISPATCHED')
        ORDER BY e.createdAt DESC
        """)
    Page<AlarmEvent> findOpenQueue(TenantId tenantId, Pageable pageable);

    @Query("""
        SELECT e FROM AlarmEvent e
        WHERE e.tenantId = :tenantId
        AND e.siteId = :siteId
        ORDER BY e.createdAt DESC
        """)
    Page<AlarmEvent> findBySite(TenantId tenantId, UUID siteId, Pageable pageable);

    @Query("""
        SELECT e FROM AlarmEvent e
        WHERE e.tenantId = :tenantId
        AND e.triggeredByGuardId = :guardId
        ORDER BY e.createdAt DESC
        """)
    List<AlarmEvent> findByGuard(TenantId tenantId, UUID guardId);
}

package za.co.handyflow.platform.controls.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.controls.domain.model.ControlException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ControlExceptionRepository extends JpaRepository<ControlException, UUID> {

    @Query("""
        SELECT e FROM ControlException e
        WHERE e.tenantId = :tenantId AND e.status = 'OPEN'
        ORDER BY e.detectedAt DESC
    """)
    List<ControlException> findOpenByTenant(@Param("tenantId") UUID tenantId);

    // NEW: Stage 3 — every exception, open AND resolved/dismissed. An
    // auditor's real question is "what got flagged and how was it
    // handled", not just "what's currently outstanding".
    @Query("""
        SELECT e FROM ControlException e
        WHERE e.tenantId = :tenantId
        ORDER BY e.detectedAt DESC
    """)
    List<ControlException> findAllByTenant(@Param("tenantId") UUID tenantId);

    // Used to resolve-by-entity: SCM knows the invoice's own ID when a
    // dispute gets overridden/cancelled, not necessarily this table's
    // own exceptionId — this is how the resolve path finds the right row.
    @Query("""
        SELECT e FROM ControlException e
        WHERE e.tenantId = :tenantId AND e.relatedEntityType = :relatedEntityType
          AND e.relatedEntityId = :relatedEntityId AND e.status = 'OPEN'
    """)
    List<ControlException> findOpenForEntity(@Param("tenantId") UUID tenantId,
                                             @Param("relatedEntityType") String relatedEntityType,
                                             @Param("relatedEntityId") UUID relatedEntityId);

    Optional<ControlException> findByTenantIdAndId(UUID tenantId, UUID id);
}
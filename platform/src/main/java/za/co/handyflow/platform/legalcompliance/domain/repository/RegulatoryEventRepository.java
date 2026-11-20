package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryEvent;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface RegulatoryEventRepository extends JpaRepository<RegulatoryEvent, UUID> {

    @Query("""
        SELECT e FROM RegulatoryEvent e
        WHERE e.entityType = :type AND e.entityId = :entityId AND (e.tenantId IS NULL OR e.tenantId = :tid)
        ORDER BY e.createdAt DESC
        """)
    List<RegulatoryEvent> forEntity(@Param("tid") UUID tenantId, @Param("type") String entityType, @Param("entityId") UUID entityId);

    /** The tenant's own events in [from, to) for the allow-listed entity types only, newest first. Used by the Audit Trail report. */
    @Query("""
        SELECT e FROM RegulatoryEvent e
        WHERE e.tenantId = :tid AND e.createdAt >= :from AND e.createdAt < :to AND e.entityType IN :types
        ORDER BY e.createdAt DESC
        """)
    List<RegulatoryEvent> inWindow(@Param("tid") UUID tenantId, @Param("from") Instant from, @Param("to") Instant to, @Param("types") Collection<String> types, Pageable pageable);
}

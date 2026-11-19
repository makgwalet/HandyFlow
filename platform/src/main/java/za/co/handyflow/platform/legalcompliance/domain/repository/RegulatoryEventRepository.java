package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.legalcompliance.domain.model.RegulatoryEvent;

import java.util.List;
import java.util.UUID;

public interface RegulatoryEventRepository extends JpaRepository<RegulatoryEvent, UUID> {

    @Query("""
        SELECT e FROM RegulatoryEvent e
        WHERE e.entityType = :type AND e.entityId = :entityId AND (e.tenantId IS NULL OR e.tenantId = :tid)
        ORDER BY e.createdAt DESC
        """)
    List<RegulatoryEvent> forEntity(@Param("tid") UUID tenantId, @Param("type") String entityType, @Param("entityId") UUID entityId);
}

package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.Event;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    @Query("""
        SELECT e FROM Event e WHERE e.tenantId = :#{#tenantId.value}
        AND e.deletedAt IS NULL
        AND (:status IS NULL OR e.status = :status)
        AND (:type IS NULL OR e.eventType = :type)
        ORDER BY e.startDatetime ASC
        """)
    Page<Event> findAll(TenantId tenantId, String status,
                        String type, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.tenantId = :#{#tenantId.value} AND e.id = :id AND e.deletedAt IS NULL")
    Optional<Event> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.tenantId = :#{#tenantId.value} AND e.deletedAt IS NULL")
    long countByTenant(TenantId tenantId);
}
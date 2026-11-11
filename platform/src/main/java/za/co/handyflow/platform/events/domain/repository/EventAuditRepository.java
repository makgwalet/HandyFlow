package za.co.handyflow.platform.events.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.events.domain.model.EventAuditRecord;

import java.util.UUID;

public interface EventAuditRepository extends JpaRepository<EventAuditRecord, UUID> {

    @Query("SELECT a FROM EventAuditRecord a WHERE a.tenantId = :tenantId AND a.eventId = :eventId " +
            "ORDER BY a.occurredAt DESC")
    Page<EventAuditRecord> findTrail(UUID tenantId, UUID eventId, Pageable pageable);
}
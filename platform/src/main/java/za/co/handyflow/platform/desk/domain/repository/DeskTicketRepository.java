package za.co.handyflow.platform.desk.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.desk.domain.model.DeskTicket;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeskTicketRepository extends JpaRepository<DeskTicket, UUID> {

    @Query("""
        SELECT t FROM DeskTicket t
        WHERE t.tenantId = :tenantId
        AND t.deletedAt IS NULL
        AND (:status IS NULL OR t.status = :status)
        AND (:channel IS NULL OR t.channel = :channel)
        AND (:priority IS NULL OR t.priority = :priority)
        ORDER BY
            CASE t.priority WHEN 'URGENT' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'NORMAL' THEN 3 ELSE 4 END,
            t.createdAt DESC
        """)
    Page<DeskTicket> findAll(TenantId tenantId, String status,
                             String channel, String priority, Pageable pageable);

    Optional<DeskTicket> findByIdAndTenantId(UUID id, TenantId tenantId);

    Optional<DeskTicket> findByPublicToken(String token);

    @Query("""
        SELECT COUNT(t) FROM DeskTicket t
        WHERE t.tenantId = :tenantId
        AND t.status = :status
        AND t.deletedAt IS NULL
        """)
    long countByStatus(TenantId tenantId, String status);

    @Query("""
        SELECT COUNT(t) FROM DeskTicket t
        WHERE t.tenantId = :tenantId
        AND t.slaBreached = true
        AND t.status NOT IN ('RESOLVED','CLOSED')
        AND t.deletedAt IS NULL
        """)
    long countSlaBreached(TenantId tenantId);

    // Find tickets past their SLA deadline — for scheduler
    // FIX: was only excluding RESOLVED/CLOSED — a ticket currently
    // WAITING_ON_CUSTOMER/WAITING_ON_THIRD_PARTY has a dueAt that hasn't
    // been adjusted yet for its current, still-ongoing pause (that
    // adjustment only happens when the ticket resumes — see
    // DeskTicket.resumeIfPaused()). Comparing that frozen deadline
    // directly would incorrectly flag a ticket as breached while it's
    // legitimately paused waiting on someone else to respond.
    @Query("""
        SELECT t FROM DeskTicket t
        WHERE t.slaBreached = false
        AND t.status NOT IN ('RESOLVED','CLOSED','WAITING_ON_CUSTOMER','WAITING_ON_THIRD_PARTY')
        AND t.dueAt IS NOT NULL
        AND t.dueAt < :now
        AND t.deletedAt IS NULL
        """)
    List<DeskTicket> findSlaBreaches(Instant now);

    @Query("SELECT COUNT(t) FROM DeskTicket t WHERE t.tenantId = :tenantId")
    int findMaxTicketSequence(TenantId tenantId);
}
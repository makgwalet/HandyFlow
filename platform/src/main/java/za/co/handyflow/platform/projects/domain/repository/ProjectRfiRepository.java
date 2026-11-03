package za.co.handyflow.platform.projects.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ProjectRfi;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRfiRepository extends JpaRepository<ProjectRfi, UUID> {

    @Query("SELECT r FROM ProjectRfi r WHERE r.projectId = :projectId AND r.tenantId = :tenantId ORDER BY r.requestedDate DESC, r.createdAt DESC")
    List<ProjectRfi> findByProject(@Param("projectId") UUID projectId, @Param("tenantId") UUID tenantId);

    @Query("SELECT r FROM ProjectRfi r WHERE r.projectId = :projectId AND r.tenantId = :tenantId AND r.status = :status ORDER BY r.requestedDate DESC")
    List<ProjectRfi> findByProjectAndStatus(@Param("projectId") UUID projectId, @Param("tenantId") UUID tenantId, @Param("status") String status);

    Optional<ProjectRfi> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Same row, locked until the transaction ends. Every RFI state change uses it, so two people
     * responding, closing or cancelling at once are serialised instead of both passing the status
     * check on stale data (which left two "responded" entries in the history).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ProjectRfi r WHERE r.id = :id AND r.tenantId = :tenantId")
    Optional<ProjectRfi> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(r) FROM ProjectRfi r WHERE r.projectId = :projectId AND r.tenantId = :tenantId AND r.status IN ('SUBMITTED','RESPONDED')")
    long countOpenByProject(@Param("projectId") UUID projectId, @Param("tenantId") UUID tenantId);

    // ── CHUNK 7 ──────────────────────────────────────────────────────────────

    /**
     * Unanswered RFIs past their due date that still have escalation levels left, on projects that
     * are still running. Across ALL tenants — used only by the nightly sweep.
     */
    @Query("""
            SELECT r FROM ProjectRfi r
            WHERE r.status = 'SUBMITTED'
              AND r.dueDate < :today
              AND r.escalationLevel < 3
              AND r.projectId IN (SELECT p.id FROM Project p WHERE p.status IN ('ACTIVE','ON_HOLD'))
            """)
    List<ProjectRfi> findOverdueForEscalation(@Param("today") LocalDate today);

    /**
     * Atomically raises the escalation level. Returns 1 only for the caller that actually moved it, so
     * if two application instances run the sweep at once, exactly one sends the notification.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ProjectRfi r SET r.escalationLevel = :level
            WHERE r.id = :id AND r.status = 'SUBMITTED' AND r.escalationLevel < :level
            """)
    int raiseEscalationLevel(@Param("id") UUID id, @Param("level") int level);
}
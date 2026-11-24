package za.co.handyflow.platform.contracting.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.contracting.domain.model.ApprovalAction;
import za.co.handyflow.platform.contracting.domain.model.ContractApproval;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractApprovalRepository extends JpaRepository<ContractApproval, UUID> {

    @Query("SELECT a FROM ContractApproval a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<ContractApproval> findOne(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ContractApproval a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<ContractApproval> findForUpdate(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ContractApproval a WHERE a.tenantId = :tenantId AND a.action = :action AND a.subjectId = :subjectId AND a.status IN ('PENDING','APPROVED')")
    Optional<ContractApproval> findOpenForUpdate(@Param("tenantId") UUID tenantId, @Param("action") ApprovalAction action, @Param("subjectId") UUID subjectId);

    @Query("SELECT a FROM ContractApproval a WHERE a.tenantId = :tenantId AND (:status IS NULL OR a.status = :status) ORDER BY a.requestedAt DESC")
    List<ContractApproval> findByStatus(@Param("tenantId") UUID tenantId, @Param("status") String status);

    @Query("SELECT a FROM ContractApproval a WHERE a.tenantId = :tenantId AND a.action = :action AND a.subjectId = :subjectId ORDER BY a.requestedAt DESC")
    List<ContractApproval> findBySubject(@Param("tenantId") UUID tenantId, @Param("action") ApprovalAction action, @Param("subjectId") UUID subjectId);

    /** Ids of requests still waiting, oldest first. The sweep takes ids and handles each in its own transaction. */
    @Query("SELECT a.id FROM ContractApproval a WHERE a.tenantId = :tenantId AND a.status = 'PENDING' ORDER BY a.requestedAt")
    List<UUID> findPendingIds(@Param("tenantId") UUID tenantId, org.springframework.data.domain.Pageable page);

    /** Requests made in a period, for the response-time report. */
    @Query("SELECT a FROM ContractApproval a WHERE a.tenantId = :tenantId AND a.requestedAt >= :from AND a.requestedAt < :to ORDER BY a.requestedAt")
    List<ContractApproval> findRequestedBetween(@Param("tenantId") UUID tenantId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);
}

package za.co.handyflow.platform.creative.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.creative.domain.model.CreJob;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreJobRepository extends JpaRepository<CreJob, UUID> {

    @Query("""
        SELECT j FROM CreJob j
        WHERE j.tenantId = :tenantId
        AND j.deletedAt IS NULL
        AND (:status IS NULL OR j.status = :status)
        ORDER BY j.createdAt DESC
        """)
    Page<CreJob> findAll(TenantId tenantId, String status, Pageable pageable);

    Optional<CreJob> findByIdAndTenantId(UUID id, TenantId tenantId);

    @Query("""
        SELECT COUNT(j) FROM CreJob j
        WHERE j.tenantId = :tenantId
        AND j.status = :status
        AND j.deletedAt IS NULL
        """)
    long countByStatus(TenantId tenantId, String status);

    // NEW: backs CreativeNotificationScheduler's overdue-job alert.
    // Cross-tenant, same reasoning as CreProofRepository's reminder query.
    @Query("""
        SELECT j FROM CreJob j
        WHERE j.deletedAt IS NULL
        AND j.status NOT IN ('APPROVED','DELIVERED','INVOICED','CANCELLED')
        AND j.dueDate < :today
        AND j.overdueAlertSentAt IS NULL
        """)
    List<CreJob> findOverdueNeedingAlert(LocalDate today);
}

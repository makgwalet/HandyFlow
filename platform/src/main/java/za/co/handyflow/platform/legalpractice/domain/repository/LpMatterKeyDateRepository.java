package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatterKeyDate;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LpMatterKeyDateRepository extends JpaRepository<LpMatterKeyDate, UUID> {

    @Query("SELECT k FROM LpMatterKeyDate k WHERE k.tenantId = :tenantId AND k.id = :id")
    Optional<LpMatterKeyDate> findActiveById(TenantId tenantId, UUID id);

    @Query("""
        SELECT k FROM LpMatterKeyDate k
        WHERE k.tenantId = :tenantId AND k.matterId = :matterId
        ORDER BY k.dueDate ASC
        """)
    List<LpMatterKeyDate> findAllForMatter(TenantId tenantId, UUID matterId);

    /**
     * Cross-tenant, non-tenant-prefixed sweep for
     * {@code LpNotificationScheduler} — mirrors
     * {@code AgScoutingRecordRepository.findFollowUpDueAcrossTenants()}'s
     * exact shape: every PENDING, unacknowledged key date whose due date
     * has arrived (today or earlier), across every tenant, in one query.
     * The scheduler itself resolves which tenant each row belongs to
     * (via the entity's own {@code tenantId}) and groups accordingly.
     */
    @Query("""
        SELECT k FROM LpMatterKeyDate k
        WHERE k.dueDate <= :today
        AND k.acknowledged = false
        AND k.status = 'PENDING'
        """)
    List<LpMatterKeyDate> findDueUnacknowledgedAcrossTenants(LocalDate today);
}

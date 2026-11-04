package za.co.handyflow.platform.legalpractice.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalpractice.domain.model.LpMatter;
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

    /**
     * Every key date due in [from, to] with its matter, for the firm-wide calendar. Rows are {@code [LpMatterKeyDate, LpMatter]}.
     * Both sides are filtered by tenant, so a key date can never be paired with another firm's matter.
     */
    @Query("""
        SELECT k, m FROM LpMatterKeyDate k, LpMatter m
        WHERE k.tenantId = :tenantId AND m.tenantId = :tenantId AND m.id = k.matterId
        AND k.dueDate >= :from AND k.dueDate <= :to
        ORDER BY k.dueDate ASC, m.matterNumber ASC
        """)
    List<Object[]> findInRangeWithMatter(TenantId tenantId, LocalDate from, LocalDate to);
}

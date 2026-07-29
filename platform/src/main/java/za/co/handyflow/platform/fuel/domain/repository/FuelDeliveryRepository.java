// fuel/domain/repository/FuelDeliveryRepository.java

package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelDelivery;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelDeliveryRepository extends JpaRepository<FuelDelivery, UUID> {

    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.deletedAt IS NULL ORDER BY d.scheduledAt DESC")
    Page<FuelDelivery> findAllActive(TenantId tenantId, Pageable pageable);

    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.status = :status AND d.deletedAt IS NULL ORDER BY d.scheduledAt ASC")
    Page<FuelDelivery> findByStatus(TenantId tenantId, String status, Pageable pageable);

    @Query("SELECT d FROM FuelDelivery d WHERE d.tenantId = :tenantId AND d.id = :id AND d.deletedAt IS NULL")
    Optional<FuelDelivery> findActiveById(TenantId tenantId, UUID id);

    // ── Notification scheduler (cross-tenant sweeps) ────────────────────────
    // "Not yet delivered" is expressed as status IN ('SCHEDULED','IN_TRANSIT') rather
    // than != 'DELIVERED', so a CANCELLED delivery is correctly excluded from both
    // sweeps without needing its own branch.

    /**
     * Not-yet-delivered deliveries whose scheduled time falls within the
     * reminder window and haven't been reminded yet.
     */
    @Query("""
        SELECT d FROM FuelDelivery d
        WHERE d.status IN ('SCHEDULED','IN_TRANSIT') AND d.deletedAt IS NULL
        AND d.scheduledAt BETWEEN :now AND :windowEnd
        AND d.reminderSentAt IS NULL
        """)
    List<FuelDelivery> findUpcomingNeedingReminder(Instant now, Instant windowEnd);

    /**
     * Not-yet-delivered deliveries whose scheduled time has already passed and
     * haven't been alerted as overdue yet.
     */
    @Query("""
        SELECT d FROM FuelDelivery d
        WHERE d.status IN ('SCHEDULED','IN_TRANSIT') AND d.deletedAt IS NULL
        AND d.scheduledAt < :now
        AND d.overdueAlertSentAt IS NULL
        """)
    List<FuelDelivery> findOverdueNeedingAlert(Instant now);
}
package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.TelematicsEvent;
import za.co.handyflow.platform.fleet.domain.model.TelematicsEventType;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Extends JpaSpecificationExecutor so the event list can combine optional filters (vehicle, driver,
 * type, dates) without a query per combination or nullable-parameter problems.
 */
public interface TelematicsEventRepository extends JpaRepository<TelematicsEvent, UUID>,
        JpaSpecificationExecutor<TelematicsEvent> {

    @Query("SELECT e FROM TelematicsEvent e WHERE e.tenantId = :tenantId AND e.id = :id")
    Optional<TelematicsEvent> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(e) > 0 FROM TelematicsEvent e WHERE e.vehicleId = :vehicleId AND e.type = :type AND e.occurredAt = :occurredAt")
    boolean existsDuplicate(UUID vehicleId, TelematicsEventType type, Instant occurredAt);

    /** Driver-attributed events in [from, to) — inputs to the driver Safety score. */
    @Query("SELECT e FROM TelematicsEvent e WHERE e.tenantId = :tenantId AND e.driverId = :driverId " +
            "AND e.occurredAt >= :from AND e.occurredAt < :to")
    List<TelematicsEvent> findByDriverInRange(TenantId tenantId, UUID driverId, Instant from, Instant to);

    @Query("SELECT e FROM TelematicsEvent e WHERE e.tenantId = :tenantId AND e.driverId IS NOT NULL " +
            "AND e.occurredAt >= :from AND e.occurredAt < :to")
    List<TelematicsEvent> findWithDriverInRange(TenantId tenantId, Instant from, Instant to);
}
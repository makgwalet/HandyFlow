package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.Accident;
import za.co.handyflow.platform.fleet.domain.model.AccidentStatus;
import za.co.handyflow.platform.fleet.domain.model.ClaimStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccidentRepository extends JpaRepository<Accident, UUID> {

    @Query("SELECT a FROM Accident a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<Accident> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT a FROM Accident a WHERE a.tenantId = :tenantId ORDER BY a.occurredAt DESC")
    Page<Accident> findAllForTenant(TenantId tenantId, Pageable pageable);

    @Query("SELECT a FROM Accident a WHERE a.tenantId = :tenantId AND a.status = :status ORDER BY a.occurredAt DESC")
    Page<Accident> findByStatus(TenantId tenantId, AccidentStatus status, Pageable pageable);

    @Query("SELECT a FROM Accident a WHERE a.tenantId = :tenantId AND a.vehicleId = :vehicleId ORDER BY a.occurredAt DESC")
    Page<Accident> findByVehicle(TenantId tenantId, UUID vehicleId, Pageable pageable);

    @Query("SELECT a FROM Accident a WHERE a.tenantId = :tenantId AND a.vehicleId = :vehicleId " +
            "AND a.status = :status ORDER BY a.occurredAt DESC")
    Page<Accident> findByVehicleAndStatus(TenantId tenantId, UUID vehicleId, AccidentStatus status, Pageable pageable);

    /** Every accident on this vehicle that has had an insurance claim lodged — the vehicle's claim history. */
    @Query("SELECT a FROM Accident a WHERE a.tenantId = :tenantId AND a.vehicleId = :vehicleId " +
            "AND a.claimStatus <> :excluded ORDER BY a.occurredAt DESC")
    List<Accident> findClaimsByVehicle(TenantId tenantId, UUID vehicleId, ClaimStatus excluded);

    /** Cases on this vehicle that say it cannot be driven and are not closed (written-off cases included). */
    @Query("SELECT a FROM Accident a WHERE a.tenantId = :tenantId AND a.vehicleId = :vehicleId AND a.vehicleDrivable = false " +
            "AND a.status IN ('REPORTED', 'UNDER_ASSESSMENT', 'IN_REPAIR', 'WRITTEN_OFF') ORDER BY a.occurredAt DESC")
    List<Accident> findUndrivableOpen(TenantId tenantId, UUID vehicleId);

    // ── NEW (chunk 6): driver score inputs ───────────────────────────────

    @Query("SELECT a FROM Accident a WHERE a.tenantId = :tenantId AND a.driverId = :driverId " +
            "AND a.occurredAt >= :from AND a.occurredAt < :to ORDER BY a.occurredAt DESC")
    List<Accident> findByDriverInRange(TenantId tenantId, UUID driverId, Instant from, Instant to);

    @Query("SELECT a FROM Accident a WHERE a.tenantId = :tenantId AND a.driverId IS NOT NULL " +
            "AND a.occurredAt >= :from AND a.occurredAt < :to")
    List<Accident> findWithDriverInRange(TenantId tenantId, Instant from, Instant to);
}
package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.Inspection;
import za.co.handyflow.platform.fleet.domain.model.InspectionStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionRepository extends JpaRepository<Inspection, UUID> {

    @Query("SELECT i FROM Inspection i WHERE i.tenantId = :tenantId AND i.id = :id")
    Optional<Inspection> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT i FROM Inspection i WHERE i.tenantId = :tenantId ORDER BY i.startedAt DESC")
    Page<Inspection> findAllForTenant(TenantId tenantId, Pageable pageable);

    @Query("SELECT i FROM Inspection i WHERE i.tenantId = :tenantId AND i.status = :status ORDER BY i.startedAt DESC")
    Page<Inspection> findAllForTenantByStatus(TenantId tenantId, InspectionStatus status, Pageable pageable);

    @Query("SELECT i FROM Inspection i WHERE i.tenantId = :tenantId AND i.vehicleId = :vehicleId ORDER BY i.startedAt DESC")
    Page<Inspection> findByVehicle(TenantId tenantId, UUID vehicleId, Pageable pageable);

    /** When this vehicle last completed an inspection from this template; null if never. */
    @Query("SELECT MAX(i.completedAt) FROM Inspection i WHERE i.tenantId = :tenantId AND i.vehicleId = :vehicleId " +
            "AND i.templateId = :templateId AND i.status = :status")
    Instant findLastCompletedAt(TenantId tenantId, UUID vehicleId, UUID templateId, InspectionStatus status);

    // ── NEW (chunk 6): driver score inputs ───────────────────────────────

    /** A driver's completed inspections finished in [from, to). Result rows are not loaded (lazy). */
    @Query("SELECT i FROM Inspection i WHERE i.tenantId = :tenantId AND i.driverId = :driverId " +
            "AND i.status = :status AND i.completedAt >= :from AND i.completedAt < :to")
    List<Inspection> findCompletedByDriverInRange(TenantId tenantId, UUID driverId, InspectionStatus status,
                                                  Instant from, Instant to);

    @Query("SELECT i FROM Inspection i WHERE i.tenantId = :tenantId AND i.driverId IS NOT NULL " +
            "AND i.status = :status AND i.completedAt >= :from AND i.completedAt < :to")
    List<Inspection> findCompletedWithDriverInRange(TenantId tenantId, InspectionStatus status,
                                                    Instant from, Instant to);
}
package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelShift;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelShiftRepository extends JpaRepository<FuelShift, UUID> {
    @Query("SELECT s FROM FuelShift s WHERE s.tenantId = :tenantId ORDER BY s.openedAt DESC")
    List<FuelShift> findRecent(TenantId tenantId, Pageable page);

    @Query("SELECT s FROM FuelShift s WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<FuelShift> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT s FROM FuelShift s WHERE s.tenantId = :tenantId AND s.status = 'CLOSED' AND s.closedAt >= :from")
    List<FuelShift> findClosedSince(TenantId tenantId, Instant from);

    @Query("SELECT COUNT(s) FROM FuelShift s WHERE s.tenantId = :tenantId AND s.status = 'OPEN'")
    long countOpen(TenantId tenantId);

    /** Closed shifts by this attendant since a time whose variance was outside the tolerance in force when they closed. */
    @Query("SELECT COUNT(s) FROM FuelShift s WHERE s.tenantId = :tenantId AND s.status = 'CLOSED' AND LOWER(s.attendantName) = LOWER(:attendant) "
            + "AND s.closedAt >= :since AND s.varianceAmount IS NOT NULL AND s.toleranceApplied IS NOT NULL AND ABS(s.varianceAmount) > s.toleranceApplied")
    long countOutsideToleranceSince(TenantId tenantId, String attendant, Instant since);
}

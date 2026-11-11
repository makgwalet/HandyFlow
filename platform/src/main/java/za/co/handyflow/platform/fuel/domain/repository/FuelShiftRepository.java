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
}

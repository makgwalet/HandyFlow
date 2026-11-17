// fuel/domain/repository/DipReadingRepository.java

package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.DipReading;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DipReadingRepository extends JpaRepository<DipReading, UUID> {

    @Query("SELECT d FROM DipReading d WHERE d.tenantId = :tenantId AND d.tankId = :tankId AND d.clientRequestId = :clientRequestId")
    java.util.Optional<DipReading> findByClientRequest(TenantId tenantId, UUID tankId, UUID clientRequestId);

    @Query("SELECT d FROM DipReading d WHERE d.tenantId = :tenantId AND d.tankId = :tankId ORDER BY d.readAt DESC")
    Page<DipReading> findByTank(TenantId tenantId, UUID tankId, Pageable pageable);

    /** Backs the reconciliation report — full (non-paginated) history within a date range, oldest first for a readable report. */
    @Query("SELECT d FROM DipReading d WHERE d.tenantId = :tenantId AND d.tankId = :tankId AND d.readAt BETWEEN :from AND :to ORDER BY d.readAt ASC")
    List<DipReading> findByTankAndReadAtBetween(TenantId tenantId, UUID tankId, Instant from, Instant to);

    /** The tanks of dip readings in a period whose variance is below the threshold (a loss). Backs the dashboard's variance alert. */
    @Query("SELECT d.tankId FROM DipReading d WHERE d.tenantId = :tenantId AND d.readAt >= :from AND d.readAt < :to AND d.varianceLitres < :threshold")
    List<UUID> tankIdsWithVarianceBelow(TenantId tenantId, Instant from, Instant to, java.math.BigDecimal threshold);
}

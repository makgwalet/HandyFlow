package za.co.handyflow.platform.fuel.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelDeliveryVariance;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelDeliveryVarianceRepository extends JpaRepository<FuelDeliveryVariance, UUID> {

    @Query("SELECT v FROM FuelDeliveryVariance v WHERE v.tenantId = :tenantId ORDER BY v.detectedAt DESC")
    List<FuelDeliveryVariance> findAllForTenant(TenantId tenantId);

    /** Variances still open or being investigated that were detected before {@code cutoff} and have not been flagged yet. Oldest first. */
    @Query("SELECT v FROM FuelDeliveryVariance v WHERE v.tenantId = :tenantId AND v.status IN ('OPEN','INVESTIGATING') AND v.detectedAt < :cutoff AND v.overdueAlertedAt IS NULL ORDER BY v.detectedAt ASC")
    List<FuelDeliveryVariance> findOverdueNotAlerted(TenantId tenantId, java.time.Instant cutoff);

    /** Marks variances as flagged. Guarded so a variance closed in the meantime is not touched, and one that was flagged is not flagged twice. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE FuelDeliveryVariance v SET v.overdueAlertedAt = :now WHERE v.tenantId = :tenantId AND v.id IN :ids AND v.overdueAlertedAt IS NULL")
    int markAlerted(TenantId tenantId, java.util.Collection<UUID> ids, java.time.Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM FuelDeliveryVariance v WHERE v.tenantId = :tenantId AND v.id = :id")
    Optional<FuelDeliveryVariance> findByIdForUpdate(TenantId tenantId, UUID id);
}

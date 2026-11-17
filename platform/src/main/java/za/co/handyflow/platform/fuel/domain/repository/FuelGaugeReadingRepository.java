package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelGaugeReading;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelGaugeReadingRepository extends JpaRepository<FuelGaugeReading, UUID> {

    @Query("SELECT g FROM FuelGaugeReading g WHERE g.tenantId = :tenantId AND g.source = :source AND g.externalId = :externalId")
    Optional<FuelGaugeReading> findByRef(TenantId tenantId, String source, String externalId);

    @Query("SELECT g FROM FuelGaugeReading g WHERE g.tenantId = :tenantId AND g.id = :id")
    Optional<FuelGaugeReading> findOwned(TenantId tenantId, UUID id);

    @Query("SELECT g FROM FuelGaugeReading g WHERE g.tenantId = :tenantId AND (:status IS NULL OR g.status = :status) ORDER BY g.receivedAt DESC")
    List<FuelGaugeReading> findByStatus(TenantId tenantId, String status, Pageable page);
}

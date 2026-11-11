package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelPump;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelPumpRepository extends JpaRepository<FuelPump, UUID> {
    @Query("SELECT p FROM FuelPump p WHERE p.tenantId = :tenantId AND p.deletedAt IS NULL ORDER BY p.name")
    List<FuelPump> findAllActive(TenantId tenantId);

    @Query("SELECT p FROM FuelPump p WHERE p.tenantId = :tenantId AND p.id = :id AND p.deletedAt IS NULL")
    Optional<FuelPump> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT p FROM FuelPump p WHERE p.tenantId = :tenantId AND p.id IN :ids AND p.deletedAt IS NULL")
    List<FuelPump> findActiveByIds(TenantId tenantId, java.util.Collection<UUID> ids);
}

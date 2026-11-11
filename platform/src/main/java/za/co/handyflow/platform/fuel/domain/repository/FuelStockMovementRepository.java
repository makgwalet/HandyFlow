package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelStockMovementRepository extends JpaRepository<FuelStockMovement, UUID> {

    @Query("SELECT m FROM FuelStockMovement m WHERE m.tenantId = :tenantId AND m.tankId = :tankId ORDER BY m.createdAt DESC")
    Page<FuelStockMovement> findByTank(TenantId tenantId, UUID tankId, Pageable pageable);

    @Query("SELECT m FROM FuelStockMovement m WHERE m.tenantId = :tenantId ORDER BY m.createdAt DESC")
    Page<FuelStockMovement> findAllForTenant(TenantId tenantId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(m.litresDelta), 0) FROM FuelStockMovement m WHERE m.tenantId = :tenantId AND m.tankId = :tankId")
    BigDecimal sumForTank(TenantId tenantId, UUID tankId);
}

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

public interface FuelDepotRepository extends JpaRepository<FuelDepot, UUID> {

    @Query("SELECT d FROM FuelDepot d WHERE d.tenantId = :tenantId AND d.deletedAt IS NULL ORDER BY d.name")
    List<FuelDepot> findAllActive(TenantId tenantId);

    @Query("SELECT d FROM FuelDepot d WHERE d.tenantId = :tenantId AND d.id = :id AND d.deletedAt IS NULL")
    Optional<FuelDepot> findActiveById(TenantId tenantId, UUID id);

    @Query("SELECT COUNT(d) > 0 FROM FuelDepot d WHERE d.tenantId = :tenantId AND UPPER(d.code) = UPPER(:code) AND d.deletedAt IS NULL AND (:excludeId IS NULL OR d.id <> :excludeId)")
    boolean codeInUse(TenantId tenantId, String code, UUID excludeId);
}

package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScStockLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScStockLocationRepository extends JpaRepository<ScStockLocation, UUID> {

    @Query("SELECT l FROM ScStockLocation l WHERE l.tenantId = :tenantId AND l.active = true ORDER BY l.name")
    List<ScStockLocation> findActiveByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT l FROM ScStockLocation l WHERE l.tenantId = :tenantId AND l.id = :id AND l.active = true")
    Optional<ScStockLocation> findActiveByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT l FROM ScStockLocation l WHERE l.tenantId = :tenantId AND l.isDefault = true AND l.active = true")
    Optional<ScStockLocation> findDefaultByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(l) FROM ScStockLocation l WHERE l.tenantId = :tenantId AND l.active = true")
    long countActiveByTenantId(@Param("tenantId") UUID tenantId);
}

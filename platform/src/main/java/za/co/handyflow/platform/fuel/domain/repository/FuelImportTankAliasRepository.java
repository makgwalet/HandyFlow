package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelImportTankAlias;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelImportTankAliasRepository extends JpaRepository<FuelImportTankAlias, UUID> {

    @Query("SELECT a FROM FuelImportTankAlias a WHERE a.tenantId = :tenantId ORDER BY a.source, a.alias")
    List<FuelImportTankAlias> findAllForTenant(TenantId tenantId);

    @Query("SELECT a FROM FuelImportTankAlias a WHERE a.tenantId = :tenantId AND a.source = :source")
    List<FuelImportTankAlias> findBySource(TenantId tenantId, String source);

    @Query("SELECT a FROM FuelImportTankAlias a WHERE a.tenantId = :tenantId AND a.source = :source AND a.alias = :alias")
    Optional<FuelImportTankAlias> findOne(TenantId tenantId, String source, String alias);

    @Query("SELECT a FROM FuelImportTankAlias a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<FuelImportTankAlias> findOwned(TenantId tenantId, UUID id);
}

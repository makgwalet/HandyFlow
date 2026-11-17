package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelImportPumpAlias;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelImportPumpAliasRepository extends JpaRepository<FuelImportPumpAlias, UUID> {

    @Query("SELECT a FROM FuelImportPumpAlias a WHERE a.tenantId = :tenantId ORDER BY a.source, a.alias")
    List<FuelImportPumpAlias> findAllForTenant(TenantId tenantId);

    @Query("SELECT a FROM FuelImportPumpAlias a WHERE a.tenantId = :tenantId AND a.source = :source")
    List<FuelImportPumpAlias> findBySource(TenantId tenantId, String source);

    @Query("SELECT a FROM FuelImportPumpAlias a WHERE a.tenantId = :tenantId AND a.source = :source AND a.alias = :alias")
    Optional<FuelImportPumpAlias> findOne(TenantId tenantId, String source, String alias);

    @Query("SELECT a FROM FuelImportPumpAlias a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<FuelImportPumpAlias> findById(TenantId tenantId, UUID id);
}

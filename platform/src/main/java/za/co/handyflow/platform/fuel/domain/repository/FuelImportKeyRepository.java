package za.co.handyflow.platform.fuel.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fuel.domain.model.FuelImportKey;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FuelImportKeyRepository extends JpaRepository<FuelImportKey, UUID> {

    /** Looked up by the hash of the presented key; callers must still check it is not revoked. The hash is globally unique, so this names its tenant. */
    Optional<FuelImportKey> findByKeyHash(String keyHash);

    @Query("SELECT k FROM FuelImportKey k WHERE k.tenantId = :tenantId ORDER BY k.createdAt DESC")
    List<FuelImportKey> findAllForTenant(TenantId tenantId);

    @Query("SELECT k FROM FuelImportKey k WHERE k.tenantId = :tenantId AND k.id = :id")
    Optional<FuelImportKey> findByTenantAndId(TenantId tenantId, UUID id);
}

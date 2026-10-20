package za.co.handyflow.platform.fleet.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.fleet.domain.model.TelematicsIngestKey;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TelematicsIngestKeyRepository extends JpaRepository<TelematicsIngestKey, UUID> {

    /** Looked up by the hash of the presented key; callers must still check it is not revoked. */
    Optional<TelematicsIngestKey> findByKeyHash(String keyHash);

    @Query("SELECT k FROM TelematicsIngestKey k WHERE k.tenantId = :tenantId ORDER BY k.createdAt DESC")
    List<TelematicsIngestKey> findAllForTenant(TenantId tenantId);

    @Query("SELECT k FROM TelematicsIngestKey k WHERE k.tenantId = :tenantId AND k.id = :id")
    Optional<TelematicsIngestKey> findByTenantAndId(TenantId tenantId, UUID id);
}
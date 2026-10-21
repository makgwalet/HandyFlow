package za.co.handyflow.platform.crm.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.crm.domain.model.CaptureKey;
import za.co.handyflow.platform.shared.TenantId;

public interface CaptureKeyRepository extends JpaRepository<CaptureKey, UUID> {
    @Query("SELECT k FROM CaptureKey k WHERE k.tenantId = :tenantId ORDER BY k.createdAt DESC, k.id")
    List<CaptureKey> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT k FROM CaptureKey k WHERE k.tenantId = :tenantId AND k.id = :id")
    Optional<CaptureKey> findByTenantAndId(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    Optional<CaptureKey> findByKeyHash(String keyHash);
}

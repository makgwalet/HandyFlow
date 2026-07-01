// security/domain/repository/ApiKeyRepository.java
package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.ApiKey;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    Optional<ApiKey> findByKeyHash(String keyHash);

    @Query("SELECT k FROM ApiKey k WHERE k.tenantId = :tenantId AND k.active = true ORDER BY k.createdAt DESC")
    List<ApiKey> findActiveByTenant(TenantId tenantId);

    @Query("SELECT k FROM ApiKey k WHERE k.tenantId = :tenantId AND k.id = :id")
    Optional<ApiKey> findByTenantAndId(TenantId tenantId, UUID id);
}
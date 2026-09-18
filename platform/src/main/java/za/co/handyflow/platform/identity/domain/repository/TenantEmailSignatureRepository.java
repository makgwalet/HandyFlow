package za.co.handyflow.platform.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.identity.domain.model.TenantEmailSignature;

import java.util.Optional;
import java.util.UUID;

public interface TenantEmailSignatureRepository extends JpaRepository<TenantEmailSignature, UUID> {
    Optional<TenantEmailSignature> findByTenantId(UUID tenantId);
}

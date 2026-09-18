package za.co.handyflow.platform.identity.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.identity.domain.model.TenantNumberingConfig;

import java.util.Optional;
import java.util.UUID;

public interface TenantNumberingConfigRepository
        extends JpaRepository<TenantNumberingConfig, TenantNumberingConfig.Key> {

    Optional<TenantNumberingConfig> findByTenantIdAndDocumentType(UUID tenantId, String documentType);
}

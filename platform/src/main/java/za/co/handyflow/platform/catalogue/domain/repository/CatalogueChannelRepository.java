package za.co.handyflow.platform.catalogue.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.catalogue.domain.model.CatalogueChannel;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogueChannelRepository extends JpaRepository<CatalogueChannel, UUID> {

    @Query("SELECT c FROM CatalogueChannel c WHERE c.tenantId = :tenantId ORDER BY c.name")
    List<CatalogueChannel> forTenant(TenantId tenantId);

    @Query("SELECT c FROM CatalogueChannel c WHERE c.tenantId = :tenantId AND c.id = :id")
    Optional<CatalogueChannel> findInTenant(TenantId tenantId, UUID id);

    @Query("SELECT c FROM CatalogueChannel c WHERE c.tenantId = :tenantId AND c.code = :code")
    Optional<CatalogueChannel> findByCode(TenantId tenantId, String code);

    @Query("SELECT COUNT(c) FROM CatalogueChannel c WHERE c.tenantId = :tenantId")
    long countForTenant(TenantId tenantId);
}

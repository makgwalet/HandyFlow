package za.co.handyflow.platform.earthmoving.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.earthmoving.domain.model.EarthSite;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EarthSiteRepository extends JpaRepository<EarthSite, UUID> {
    @Query("SELECT s FROM EarthSite s WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<EarthSite> findByTenantAndId(TenantId tenantId, UUID id);

    @Query("SELECT s FROM EarthSite s WHERE s.tenantId = :tenantId AND (:includeInactive = true OR s.active = true) ORDER BY s.name")
    List<EarthSite> findAllForTenant(TenantId tenantId, boolean includeInactive);

    @Query("SELECT s FROM EarthSite s WHERE s.tenantId = :tenantId AND s.active = true AND LOWER(TRIM(s.name)) = LOWER(TRIM(:name))")
    List<EarthSite> findActiveByExactName(TenantId tenantId, String name);
}

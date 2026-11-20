package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.CustomReport;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomReportRepository extends JpaRepository<CustomReport, UUID> {

    @Query("SELECT x FROM CustomReport x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<CustomReport> findForTenant(TenantId tenantId, UUID id);

    /** The caller's own reports and everyone's shared ones. */
    @Query("SELECT x FROM CustomReport x WHERE x.tenantId = :tenantId AND (x.ownerUserId = :userId OR x.shared = true) ORDER BY lower(x.name)")
    List<CustomReport> visibleTo(TenantId tenantId, UUID userId);

    @Query("SELECT COUNT(x) FROM CustomReport x WHERE x.tenantId = :tenantId AND x.ownerUserId = :userId")
    long countOwned(TenantId tenantId, UUID userId);

    @Query("SELECT COUNT(x) > 0 FROM CustomReport x WHERE x.tenantId = :tenantId AND x.ownerUserId = :userId AND lower(x.name) = lower(:name) AND (:excludeId IS NULL OR x.id <> :excludeId)")
    boolean nameTaken(TenantId tenantId, UUID userId, String name, UUID excludeId);
}

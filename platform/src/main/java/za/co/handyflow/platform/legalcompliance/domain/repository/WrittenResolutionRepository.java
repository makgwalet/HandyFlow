package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.WrittenResolution;
import za.co.handyflow.platform.legalcompliance.domain.model.WrittenStatus;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WrittenResolutionRepository extends JpaRepository<WrittenResolution, UUID> {

    @Query("SELECT x FROM WrittenResolution x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<WrittenResolution> findForTenant(TenantId tenantId, UUID id);

    @Query("SELECT x FROM WrittenResolution x WHERE x.tenantId = :tenantId AND (:committeeId IS NULL OR x.committeeId = :committeeId) AND (:status IS NULL OR x.status = :status) ORDER BY x.createdAt DESC")
    List<WrittenResolution> search(TenantId tenantId, UUID committeeId, WrittenStatus status);

    @Query("SELECT x FROM WrittenResolution x WHERE x.tenantId = :tenantId AND x.id IN :ids")
    List<WrittenResolution> findForIds(TenantId tenantId, Collection<UUID> ids);

    /** Cross-tenant — the daily sweep. */
    @Query("SELECT x FROM WrittenResolution x WHERE x.status = za.co.handyflow.platform.legalcompliance.domain.model.WrittenStatus.OPEN")
    List<WrittenResolution> findOpenAcrossTenants();

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}

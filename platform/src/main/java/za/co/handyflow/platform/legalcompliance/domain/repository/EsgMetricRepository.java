package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.EsgMetric;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EsgMetricRepository extends JpaRepository<EsgMetric, UUID> {

    @Query("SELECT x FROM EsgMetric x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<EsgMetric> findForTenant(TenantId tenantId, UUID id);

    @Query("""
        SELECT x FROM EsgMetric x WHERE x.tenantId = :tenantId
        AND (:includeRetired = TRUE OR x.status = za.co.handyflow.platform.legalcompliance.domain.model.EsgMetricStatus.ACTIVE)
        ORDER BY x.category, x.name
        """)
    List<EsgMetric> search(TenantId tenantId, boolean includeRetired);

    /** Cross-tenant — daily sweep. */
    @Query("SELECT x FROM EsgMetric x WHERE x.status = za.co.handyflow.platform.legalcompliance.domain.model.EsgMetricStatus.ACTIVE")
    List<EsgMetric> findActiveAcrossTenants();

    @Query(value = "SELECT nextval('legalcompliance_ref_seq')", nativeQuery = true)
    long nextRef();
}

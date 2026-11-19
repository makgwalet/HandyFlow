package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.legalcompliance.domain.model.EsgReading;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EsgReadingRepository extends JpaRepository<EsgReading, UUID> {

    @Query("SELECT x FROM EsgReading x WHERE x.tenantId = :tenantId AND x.id = :id")
    Optional<EsgReading> findForTenant(TenantId tenantId, UUID id);

    /** Every reading for the metric, corrected ones included (flagged), newest period first. */
    @Query("SELECT x FROM EsgReading x WHERE x.tenantId = :tenantId AND x.metricId = :metricId ORDER BY x.periodEnd DESC, x.createdAt DESC")
    List<EsgReading> findForMetric(TenantId tenantId, UUID metricId);

    /** Only the readings still standing, for the whole tenant. */
    @Query("SELECT x FROM EsgReading x WHERE x.tenantId = :tenantId AND x.supersededAt IS NULL ORDER BY x.periodEnd DESC")
    List<EsgReading> findCurrentForTenant(TenantId tenantId);

    @Query("SELECT x FROM EsgReading x WHERE x.tenantId = :tenantId AND x.metricId = :metricId AND x.periodEnd = :periodEnd AND x.supersededAt IS NULL")
    Optional<EsgReading> findCurrentForPeriod(TenantId tenantId, UUID metricId, java.time.LocalDate periodEnd);
}

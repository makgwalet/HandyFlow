package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ComplianceCostRate;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ComplianceCostRateRepository extends JpaRepository<ComplianceCostRate, UUID> {

    @Query("SELECT r FROM ComplianceCostRate r WHERE r.tenantId.value = :#{#tenantId.value} ORDER BY r.userId, r.effectiveFrom DESC")
    List<ComplianceCostRate> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT COUNT(r) FROM ComplianceCostRate r WHERE r.tenantId.value = :#{#tenantId.value} AND r.userId = :userId AND r.effectiveFrom = :day")
    long countOnDay(@Param("tenantId") TenantId tenantId, @Param("userId") UUID userId, @Param("day") LocalDate day);
}

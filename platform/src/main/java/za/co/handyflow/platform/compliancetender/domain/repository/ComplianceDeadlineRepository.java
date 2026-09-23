package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceDeadline;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceDeadlineRepository extends JpaRepository<ComplianceDeadline, UUID> {

    @Query("SELECT d FROM ComplianceDeadline d WHERE d.tenantId = :#{#tenantId.value} AND d.id = :id")
    Optional<ComplianceDeadline> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT d FROM ComplianceDeadline d WHERE d.tenantId = :#{#tenantId.value} " +
           "AND d.status = 'PENDING' ORDER BY d.dueDate ASC")
    List<ComplianceDeadline> findPendingForTenant(@Param("tenantId") TenantId tenantId);

    /** Cross-tenant sweep, same pattern as the other two repositories' own. */
    @Query("SELECT d FROM ComplianceDeadline d WHERE d.status = 'PENDING' AND d.dueDate <= :cutoff")
    List<ComplianceDeadline> findAllPendingDueAcrossTenants(@Param("cutoff") LocalDate cutoff);
}

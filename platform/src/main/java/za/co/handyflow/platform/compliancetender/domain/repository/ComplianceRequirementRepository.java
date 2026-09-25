package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceRequirement;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceRequirementRepository extends JpaRepository<ComplianceRequirement, UUID> {

    @Query("SELECT r FROM ComplianceRequirement r WHERE r.tenantId.value = :#{#tenantId.value} AND r.id = :id")
    Optional<ComplianceRequirement> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM ComplianceRequirement r WHERE r.tenantId.value = :#{#tenantId.value} ORDER BY r.code")
    List<ComplianceRequirement> findAllForTenant(@Param("tenantId") TenantId tenantId);

    /** The current (highest-version) row for a given code — used when applying today's rules. */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.tenantId.value = :#{#tenantId.value} AND r.code = :code " +
           "ORDER BY r.requirementVersion DESC LIMIT 1")
    Optional<ComplianceRequirement> findLatestByCode(@Param("tenantId") TenantId tenantId, @Param("code") String code);

    /** Every version ever recorded for one code, most recent first — the audit trail behind ComplianceRequirement's own versioning design. */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.tenantId.value = :#{#tenantId.value} AND r.code = :code " +
           "ORDER BY r.requirementVersion DESC")
    List<ComplianceRequirement> findAllVersionsByCode(@Param("tenantId") TenantId tenantId, @Param("code") String code);
}

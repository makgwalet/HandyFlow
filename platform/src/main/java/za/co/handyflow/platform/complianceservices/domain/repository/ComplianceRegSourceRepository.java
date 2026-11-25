package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceRegSourceRepository extends JpaRepository<ComplianceRegSource, UUID> {
    @Query("SELECT s FROM ComplianceRegSource s WHERE s.tenantId.value = :#{#tenantId.value} ORDER BY s.name")
    List<ComplianceRegSource> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT s FROM ComplianceRegSource s WHERE s.tenantId.value = :#{#tenantId.value} AND s.id = :id")
    Optional<ComplianceRegSource> findOne(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT s FROM ComplianceRegSource s WHERE s.tenantId.value = :#{#tenantId.value} AND lower(s.name) = lower(:name)")
    Optional<ComplianceRegSource> findByName(@Param("tenantId") TenantId tenantId, @Param("name") String name);
}

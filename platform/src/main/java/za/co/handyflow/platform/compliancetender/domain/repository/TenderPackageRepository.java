package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPackage;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenderPackageRepository extends JpaRepository<TenderPackage, UUID> {

    @Query("SELECT p FROM TenderPackage p WHERE p.tenantId.value = :#{#tenantId.value} AND p.tenderId = :tenderId ORDER BY p.versionNo DESC")
    List<TenderPackage> findByTender(@Param("tenantId") TenantId tenantId, @Param("tenderId") UUID tenderId);

    @Query("SELECT p FROM TenderPackage p WHERE p.tenantId.value = :#{#tenantId.value} AND p.id = :id")
    Optional<TenderPackage> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT COALESCE(MAX(p.versionNo), 0) FROM TenderPackage p WHERE p.tenantId.value = :#{#tenantId.value} AND p.tenderId = :tenderId")
    int maxVersion(@Param("tenantId") TenantId tenantId, @Param("tenderId") UUID tenderId);
}

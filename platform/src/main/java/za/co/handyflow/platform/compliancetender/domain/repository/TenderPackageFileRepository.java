package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPackageFile;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface TenderPackageFileRepository extends JpaRepository<TenderPackageFile, UUID> {

    @Query("SELECT f FROM TenderPackageFile f WHERE f.tenantId.value = :#{#tenantId.value} AND f.packageId = :packageId ORDER BY f.sequenceNo")
    List<TenderPackageFile> findByPackage(@Param("tenantId") TenantId tenantId, @Param("packageId") UUID packageId);
}

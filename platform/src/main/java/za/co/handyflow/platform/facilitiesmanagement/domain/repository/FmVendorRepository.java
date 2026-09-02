package za.co.handyflow.platform.facilitiesmanagement.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.facilitiesmanagement.domain.model.FmVendor;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface FmVendorRepository extends JpaRepository<FmVendor, UUID> {

    @Query("SELECT v FROM FmVendor v WHERE v.tenantId = :#{#tenantId.value} AND v.id = :id AND v.deletedAt IS NULL")
    Optional<FmVendor> findActiveById(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT v FROM FmVendor v WHERE v.tenantId = :#{#tenantId.value} AND v.deletedAt IS NULL")
    Page<FmVendor> findAllActive(@Param("tenantId") TenantId tenantId, Pageable pageable);
}

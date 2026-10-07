package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.TenderRate;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenderRateRepository extends JpaRepository<TenderRate, UUID> {

    @Query("SELECT r FROM TenderRate r WHERE r.tenantId.value = :#{#tenantId.value} ORDER BY r.category, LOWER(r.description), LOWER(r.supplier)")
    List<TenderRate> findAllForTenant(@Param("tenantId") TenantId tenantId);

    @Query("SELECT r FROM TenderRate r WHERE r.tenantId.value = :#{#tenantId.value} AND r.id = :id")
    Optional<TenderRate> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT r FROM TenderRate r WHERE r.tenantId.value = :#{#tenantId.value} AND LOWER(r.description) = LOWER(:description) AND LOWER(r.unit) = LOWER(:unit) AND LOWER(r.supplier) = LOWER(:supplier)")
    Optional<TenderRate> findByKey(@Param("tenantId") TenantId tenantId, @Param("description") String description, @Param("unit") String unit, @Param("supplier") String supplier);
}

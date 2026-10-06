package za.co.handyflow.platform.compliancetender.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.compliancetender.domain.model.TenderPricing;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface TenderPricingRepository extends JpaRepository<TenderPricing, UUID> {

    @Query("SELECT p FROM TenderPricing p WHERE p.tenantId.value = :#{#tenantId.value} AND p.tenderId = :tenderId")
    Optional<TenderPricing> findByTender(@Param("tenantId") TenantId tenantId, @Param("tenderId") UUID tenderId);
}

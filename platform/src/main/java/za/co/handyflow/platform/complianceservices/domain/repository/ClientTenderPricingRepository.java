package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderPricing;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface ClientTenderPricingRepository extends JpaRepository<ClientTenderPricing, UUID> {

    @Query("SELECT p FROM ClientTenderPricing p WHERE p.tenantId.value = :#{#tenantId.value} AND p.tenderId = :tenderId")
    Optional<ClientTenderPricing> findByTender(@Param("tenantId") TenantId tenantId, @Param("tenderId") UUID tenderId);
}

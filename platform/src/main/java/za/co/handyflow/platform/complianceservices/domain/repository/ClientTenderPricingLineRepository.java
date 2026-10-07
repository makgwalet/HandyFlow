package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTenderPricingLine;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientTenderPricingLineRepository extends JpaRepository<ClientTenderPricingLine, UUID> {

    @Query("SELECT l FROM ClientTenderPricingLine l WHERE l.tenantId.value = :#{#tenantId.value} AND l.id = :id")
    Optional<ClientTenderPricingLine> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT l FROM ClientTenderPricingLine l WHERE l.tenantId.value = :#{#tenantId.value} AND l.tenderId = :tenderId " +
           "ORDER BY l.sortOrder, l.createdAt")
    List<ClientTenderPricingLine> findByTender(@Param("tenantId") TenantId tenantId, @Param("tenderId") UUID tenderId);

    @Query("SELECT COALESCE(MAX(l.sortOrder), 0) FROM ClientTenderPricingLine l WHERE l.tenantId.value = :#{#tenantId.value} AND l.tenderId = :tenderId")
    int maxSortOrder(@Param("tenantId") TenantId tenantId, @Param("tenderId") UUID tenderId);
}

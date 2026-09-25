package za.co.handyflow.platform.complianceservices.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.complianceservices.domain.model.ClientTender;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

public interface ClientTenderRepository extends JpaRepository<ClientTender, UUID> {

    @Query("SELECT t FROM ClientTender t WHERE t.tenantId.value = :#{#tenantId.value} AND t.id = :id")
    Optional<ClientTender> findByIdForTenant(@Param("tenantId") TenantId tenantId, @Param("id") UUID id);

    @Query("SELECT t FROM ClientTender t WHERE t.tenantId.value = :#{#tenantId.value} AND t.clientId = :clientId " +
           "AND (:status IS NULL OR t.status = :status) ORDER BY t.closingDate ASC NULLS LAST")
    Page<ClientTender> findByClient(@Param("tenantId") TenantId tenantId, @Param("clientId") UUID clientId,
                                    @Param("status") String status, Pageable pageable);
}

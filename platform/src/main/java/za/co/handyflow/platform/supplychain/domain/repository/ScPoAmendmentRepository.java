package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScPoAmendment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScPoAmendmentRepository extends JpaRepository<ScPoAmendment, UUID> {

    @Query("SELECT a FROM ScPoAmendment a WHERE a.tenantId = :tenantId AND a.id = :id")
    Optional<ScPoAmendment> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT a FROM ScPoAmendment a WHERE a.tenantId = :tenantId AND a.purchaseOrderId = :poId ORDER BY a.amendmentNumber DESC")
    List<ScPoAmendment> findAllForPo(@Param("tenantId") UUID tenantId, @Param("poId") UUID poId);

    @Query("SELECT a FROM ScPoAmendment a WHERE a.tenantId = :tenantId AND a.purchaseOrderId = :poId AND a.status = 'PENDING_APPROVAL'")
    Optional<ScPoAmendment> findOpenForPo(@Param("tenantId") UUID tenantId, @Param("poId") UUID poId);

    @Query("SELECT MAX(a.amendmentNumber) FROM ScPoAmendment a WHERE a.tenantId = :tenantId AND a.purchaseOrderId = :poId")
    Integer maxNumberForPo(@Param("tenantId") UUID tenantId, @Param("poId") UUID poId);
}

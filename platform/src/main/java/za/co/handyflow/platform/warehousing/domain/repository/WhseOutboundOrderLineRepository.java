package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrderLine;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhseOutboundOrderLineRepository extends JpaRepository<WhseOutboundOrderLine, UUID> {

    @Query("SELECT l FROM WhseOutboundOrderLine l WHERE l.tenantId = :tenantId AND l.orderId = :orderId")
    List<WhseOutboundOrderLine> findByOrder(@Param("tenantId") UUID tenantId, @Param("orderId") UUID orderId);

    @Query("SELECT l FROM WhseOutboundOrderLine l WHERE l.tenantId = :tenantId AND l.id = :id")
    Optional<WhseOutboundOrderLine> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}

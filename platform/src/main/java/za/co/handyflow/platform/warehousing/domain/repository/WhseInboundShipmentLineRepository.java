package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipmentLine;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhseInboundShipmentLineRepository extends JpaRepository<WhseInboundShipmentLine, UUID> {

    @Query("SELECT l FROM WhseInboundShipmentLine l WHERE l.tenantId = :tenantId AND l.shipmentId = :shipmentId")
    List<WhseInboundShipmentLine> findByShipment(@Param("tenantId") UUID tenantId, @Param("shipmentId") UUID shipmentId);

    @Query("SELECT l FROM WhseInboundShipmentLine l WHERE l.tenantId = :tenantId AND l.id = :id")
    Optional<WhseInboundShipmentLine> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    /**
     * Every line received across the client's shipments since a given instant — used by WhseBillingService to
     * compute the handling (receiving) fee component for a billing period without needing a dedicated event log.
     */
    @Query("""
        SELECT l FROM WhseInboundShipmentLine l WHERE l.tenantId = :tenantId AND l.receivedQty > 0
        AND l.shipmentId IN (SELECT s.id FROM WhseInboundShipment s WHERE s.tenantId = :tenantId AND s.clientId = :clientId
        AND s.updatedAt >= :since)
        """)
    List<WhseInboundShipmentLine> findReceivedForClientSince(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId,
                                                               @Param("since") Instant since);
}

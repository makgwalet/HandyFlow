package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseOutboundOrder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhseOutboundOrderRepository extends JpaRepository<WhseOutboundOrder, UUID> {

    @Query("SELECT o FROM WhseOutboundOrder o WHERE o.tenantId = :tenantId AND o.id = :id")
    Optional<WhseOutboundOrder> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT o FROM WhseOutboundOrder o WHERE o.tenantId = :tenantId AND o.clientId = :clientId ORDER BY o.createdAt DESC")
    Page<WhseOutboundOrder> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    /** Cross-tenant sweep for the overdue-to-ship notification scheduler. */
    @Query("SELECT o FROM WhseOutboundOrder o WHERE o.status IN ('PENDING', 'PICKING', 'PACKED') AND o.requestedShipDate < :today")
    List<WhseOutboundOrder> findOverdueAcrossTenants(@Param("today") LocalDate today);

    /** Orders shipped for a client since a given date — used by WhseBillingService for the pick/pack handling fee component. */
    @Query("SELECT o FROM WhseOutboundOrder o WHERE o.tenantId = :tenantId AND o.clientId = :clientId AND o.status = 'SHIPPED' AND o.shippedDate >= :since")
    List<WhseOutboundOrder> findShippedForClientSince(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, @Param("since") LocalDate since);
}

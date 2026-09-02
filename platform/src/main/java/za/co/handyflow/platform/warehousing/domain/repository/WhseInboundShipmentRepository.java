package za.co.handyflow.platform.warehousing.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.warehousing.domain.model.WhseInboundShipment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WhseInboundShipmentRepository extends JpaRepository<WhseInboundShipment, UUID> {

    @Query("SELECT s FROM WhseInboundShipment s WHERE s.tenantId = :tenantId AND s.id = :id")
    Optional<WhseInboundShipment> findByTenantAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("SELECT s FROM WhseInboundShipment s WHERE s.tenantId = :tenantId AND s.clientId = :clientId ORDER BY s.expectedDate DESC")
    Page<WhseInboundShipment> findByClient(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, Pageable pageable);

    /** Cross-tenant sweep for the overdue-to-receive notification scheduler — still expected, past the expected date. */
    @Query("SELECT s FROM WhseInboundShipment s WHERE s.status IN ('EXPECTED', 'PARTIALLY_RECEIVED') AND s.expectedDate < :today")
    List<WhseInboundShipment> findOverdueAcrossTenants(@Param("today") LocalDate today);
}

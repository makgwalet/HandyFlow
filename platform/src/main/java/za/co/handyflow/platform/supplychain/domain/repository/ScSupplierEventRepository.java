package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScSupplierEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ScSupplierEventRepository extends JpaRepository<ScSupplierEvent, UUID> {

    @Query("SELECT e FROM ScSupplierEvent e WHERE e.tenantId = :tenantId AND e.supplierId = :supplierId ORDER BY e.createdAt DESC")
    List<ScSupplierEvent> findAllForSupplier(@Param("tenantId") UUID tenantId, @Param("supplierId") UUID supplierId);

    @Query("SELECT COUNT(e) FROM ScSupplierEvent e WHERE e.tenantId = :tenantId AND e.supplierId = :supplierId AND e.eventType = :eventType AND e.createdAt >= :since")
    long countSince(@Param("tenantId") UUID tenantId, @Param("supplierId") UUID supplierId,
                    @Param("eventType") String eventType, @Param("since") Instant since);
}

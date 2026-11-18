package za.co.handyflow.platform.supplychain.domain.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScRfqEvent;

public interface ScRfqEventRepository extends JpaRepository<ScRfqEvent, UUID> {

    @Query("SELECT e FROM ScRfqEvent e WHERE e.tenantId = :tenantId AND e.rfqId = :rfqId ORDER BY e.createdAt DESC")
    List<ScRfqEvent> findAllForRfq(@Param("tenantId") UUID tenantId, @Param("rfqId") UUID rfqId);
}

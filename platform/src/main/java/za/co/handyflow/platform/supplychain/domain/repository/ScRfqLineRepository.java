package za.co.handyflow.platform.supplychain.domain.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScRfqLine;

public interface ScRfqLineRepository extends JpaRepository<ScRfqLine, UUID> {

    @Query("SELECT l FROM ScRfqLine l WHERE l.tenantId = :tenantId AND l.rfqId = :rfqId ORDER BY l.lineNumber")
    List<ScRfqLine> findByRfq(@Param("tenantId") UUID tenantId, @Param("rfqId") UUID rfqId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ScRfqLine l WHERE l.tenantId = :tenantId AND l.rfqId = :rfqId")
    void deleteByRfq(@Param("tenantId") UUID tenantId, @Param("rfqId") UUID rfqId);
}

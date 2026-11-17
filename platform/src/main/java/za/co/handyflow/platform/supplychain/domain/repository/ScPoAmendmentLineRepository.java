package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScPoAmendmentLine;

import java.util.List;
import java.util.UUID;

public interface ScPoAmendmentLineRepository extends JpaRepository<ScPoAmendmentLine, UUID> {

    @Query("SELECT l FROM ScPoAmendmentLine l WHERE l.tenantId = :tenantId AND l.amendmentId = :amendmentId ORDER BY l.id")
    List<ScPoAmendmentLine> findByAmendment(@Param("tenantId") UUID tenantId, @Param("amendmentId") UUID amendmentId);
}

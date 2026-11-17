package za.co.handyflow.platform.supplychain.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.supplychain.domain.model.ScRequisitionLine;

import java.util.List;
import java.util.UUID;

public interface ScRequisitionLineRepository extends JpaRepository<ScRequisitionLine, UUID> {

    @Query("SELECT l FROM ScRequisitionLine l WHERE l.tenantId = :tenantId AND l.requisitionId = :requisitionId ORDER BY l.lineNumber")
    List<ScRequisitionLine> findByRequisition(@Param("tenantId") UUID tenantId, @Param("requisitionId") UUID requisitionId);

    @Modifying
    @Query("DELETE FROM ScRequisitionLine l WHERE l.tenantId = :tenantId AND l.requisitionId = :requisitionId")
    void deleteByRequisition(@Param("tenantId") UUID tenantId, @Param("requisitionId") UUID requisitionId);
}

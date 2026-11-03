package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ChangeOrderCostLine;

import java.util.List;
import java.util.UUID;

public interface ChangeOrderCostLineRepository extends JpaRepository<ChangeOrderCostLine, UUID> {

    @Query("SELECT l FROM ChangeOrderCostLine l WHERE l.changeOrderId = :coId ORDER BY l.createdAt, l.id")
    List<ChangeOrderCostLine> findByChangeOrder(@Param("coId") UUID changeOrderId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ChangeOrderCostLine l WHERE l.changeOrderId = :coId")
    void deleteByChangeOrder(@Param("coId") UUID changeOrderId);
}
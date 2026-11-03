package za.co.handyflow.platform.projects.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.projects.domain.model.ChangeOrderTask;

import java.util.List;
import java.util.UUID;

public interface ChangeOrderTaskRepository extends JpaRepository<ChangeOrderTask, UUID> {

    @Query("SELECT t FROM ChangeOrderTask t WHERE t.changeOrderId = :coId")
    List<ChangeOrderTask> findByChangeOrder(@Param("coId") UUID changeOrderId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ChangeOrderTask t WHERE t.changeOrderId = :coId")
    void deleteByChangeOrder(@Param("coId") UUID changeOrderId);
}
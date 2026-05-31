package za.co.handyflow.platform.tasks.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.tasks.domain.model.TaskTimeLog;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TaskTimeLogRepository extends JpaRepository<TaskTimeLog, UUID> {

    List<TaskTimeLog> findByTaskIdOrderByLoggedDateDesc(UUID taskId);

    @Query("SELECT COALESCE(SUM(l.hours), 0) FROM TaskTimeLog l WHERE l.taskId = :taskId")
    BigDecimal sumHoursByTask(UUID taskId);
}

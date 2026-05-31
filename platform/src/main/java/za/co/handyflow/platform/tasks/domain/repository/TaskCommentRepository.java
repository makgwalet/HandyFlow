package za.co.handyflow.platform.tasks.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.tasks.domain.model.TaskComment;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {
    List<TaskComment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);
}

package za.co.handyflow.platform.tasks.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.tasks.domain.model.TaskAttachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, UUID> {

    List<TaskAttachment> findByTaskIdOrderByCreatedAtDesc(UUID taskId);

    /** Scoped by taskId too, not just id — prevents downloading/deleting an attachment via a mismatched task path. */
    Optional<TaskAttachment> findByIdAndTaskId(UUID id, UUID taskId);
}
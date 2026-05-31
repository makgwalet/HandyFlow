package za.co.handyflow.platform.desk.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.desk.domain.model.DeskComment;

import java.util.List;
import java.util.UUID;

public interface DeskCommentRepository extends JpaRepository<DeskComment, UUID> {
    List<DeskComment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);
    // Internal notes excluded for customer-facing view
    List<DeskComment> findByTicketIdAndIsInternalFalseOrderByCreatedAtAsc(UUID ticketId);
}

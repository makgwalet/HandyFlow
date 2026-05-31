package za.co.handyflow.platform.creative.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.creative.domain.model.CreProofComment;

import java.util.List;
import java.util.UUID;

public interface CreProofCommentRepository extends JpaRepository<CreProofComment, UUID> {
    List<CreProofComment> findByProofIdOrderByCreatedAtAsc(UUID proofId);
}

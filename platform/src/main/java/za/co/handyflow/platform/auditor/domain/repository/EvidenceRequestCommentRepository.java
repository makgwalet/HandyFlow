package za.co.handyflow.platform.auditor.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.auditor.domain.model.EvidenceRequestComment;

import java.util.List;
import java.util.UUID;

public interface EvidenceRequestCommentRepository extends JpaRepository<EvidenceRequestComment, UUID> {
    List<EvidenceRequestComment> findByRequestIdOrderByCreatedAtAsc(UUID requestId);
}

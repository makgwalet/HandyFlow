package za.co.handyflow.platform.creative.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.creative.domain.model.CreProofApprover;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreProofApproverRepository extends JpaRepository<CreProofApprover, UUID> {
    List<CreProofApprover> findByProofIdOrderByApprovalOrderAsc(UUID proofId);
    Optional<CreProofApprover> findByApprovalToken(String token);
    void deleteByProofId(UUID proofId);
}

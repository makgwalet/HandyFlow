package za.co.handyflow.platform.creative.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.creative.domain.model.CreDeliverable;

import java.util.List;
import java.util.UUID;

public interface CreDeliverableRepository extends JpaRepository<CreDeliverable, UUID> {
    List<CreDeliverable> findByJobIdOrderByCreatedAtDesc(UUID jobId);
}

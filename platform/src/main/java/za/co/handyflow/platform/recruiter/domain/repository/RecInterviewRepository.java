package za.co.handyflow.platform.recruiter.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.recruiter.domain.model.RecInterview;

import java.util.List;
import java.util.UUID;

public interface RecInterviewRepository extends JpaRepository<RecInterview, UUID> {
    List<RecInterview> findByApplicationIdOrderByScheduledAtAsc(UUID applicationId);
}

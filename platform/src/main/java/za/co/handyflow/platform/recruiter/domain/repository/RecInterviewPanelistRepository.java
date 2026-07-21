package za.co.handyflow.platform.recruiter.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.handyflow.platform.recruiter.domain.model.RecInterviewPanelist;

import java.util.List;
import java.util.UUID;

public interface RecInterviewPanelistRepository extends JpaRepository<RecInterviewPanelist, UUID> {
    List<RecInterviewPanelist> findByInterviewId(UUID interviewId);
}
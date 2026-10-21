package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrErEvent;

import java.util.List;
import java.util.UUID;

public interface HrErEventRepository extends JpaRepository<HrErEvent, UUID> {

    @Query("SELECT e FROM HrErEvent e WHERE e.caseId = :caseId ORDER BY e.doneAt")
    List<HrErEvent> forCase(UUID caseId);
}

package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrLetterEvent;

import java.util.List;
import java.util.UUID;

public interface HrLetterEventRepository extends JpaRepository<HrLetterEvent, UUID> {

    @Query("SELECT e FROM HrLetterEvent e WHERE e.letterId = :letterId ORDER BY e.doneAt")
    List<HrLetterEvent> forLetter(UUID letterId);
}

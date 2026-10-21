package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrErStep;

import java.util.List;
import java.util.UUID;

public interface HrErStepRepository extends JpaRepository<HrErStep, UUID> {

    @Query("SELECT s FROM HrErStep s WHERE s.caseId = :caseId ORDER BY s.seq")
    List<HrErStep> forCase(UUID caseId);
}

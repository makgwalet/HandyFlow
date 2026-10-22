package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.*;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrPerfCheckinRepository extends JpaRepository<HrPerfCheckin, UUID> {

    @Query("SELECT c FROM HrPerfCheckin c WHERE c.planId = :planId ORDER BY c.onDate, c.doneAt")
    List<HrPerfCheckin> forPlan(UUID planId);
}

package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrErCharge;

import java.util.List;
import java.util.UUID;

public interface HrErChargeRepository extends JpaRepository<HrErCharge, UUID> {

    @Query("SELECT c FROM HrErCharge c WHERE c.caseId = :caseId ORDER BY c.seq")
    List<HrErCharge> forCase(UUID caseId);
}

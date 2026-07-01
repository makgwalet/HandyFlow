// security/domain/repository/PayrollLineItemRepository.java
package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.PayrollLineItem;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.UUID;

public interface PayrollLineItemRepository extends JpaRepository<PayrollLineItem, UUID> {

    @Query("SELECT li FROM PayrollLineItem li WHERE li.periodId = :periodId ORDER BY li.guardId, li.shiftStartAt")
    List<PayrollLineItem> findByPeriod(UUID periodId);

    @Query("SELECT li FROM PayrollLineItem li WHERE li.periodId = :periodId AND li.guardId = :guardId ORDER BY li.shiftStartAt")
    List<PayrollLineItem> findByPeriodAndGuard(UUID periodId, UUID guardId);

    @Query("SELECT COUNT(li) > 0 FROM PayrollLineItem li WHERE li.periodId = :periodId AND li.shiftId = :shiftId")
    boolean existsForShift(UUID periodId, UUID shiftId);

    @Query("SELECT COALESCE(SUM(li.grossAmountCents), 0) FROM PayrollLineItem li WHERE li.periodId = :periodId")
    long sumGrossAmountForPeriod(UUID periodId);
}
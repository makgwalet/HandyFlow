package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayDeadline;

import java.util.List;
import java.util.UUID;

public interface PayDeadlineRepository extends JpaRepository<PayDeadline, UUID> {

    @Query("SELECT d FROM PayDeadline d WHERE d.payClientId = :clientId ORDER BY d.adjustedDueDate ASC")
    List<PayDeadline> findByClient(@Param("clientId") UUID clientId);

    @Query("""
        SELECT COUNT(d) > 0 FROM PayDeadline d
        WHERE d.payClientId = :clientId AND d.deadlineType = :type
        AND d.periodYear = :year AND (d.periodMonth = :month OR (d.periodMonth IS NULL AND :month IS NULL))
    """)
    boolean existsForPeriod(@Param("clientId") UUID clientId, @Param("type") String type,
                            @Param("year") int year, @Param("month") Integer month);
}
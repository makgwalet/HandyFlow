package za.co.handyflow.platform.payrollbureau.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.handyflow.platform.payrollbureau.domain.model.PayEmp201Return;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayEmp201ReturnRepository extends JpaRepository<PayEmp201Return, UUID> {

    @Query("SELECT r FROM PayEmp201Return r WHERE r.tenantId = :tenantId AND r.payClientId = :clientId AND r.taxMonth = :month")
    Optional<PayEmp201Return> findByMonth(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId, @Param("month") LocalDate month);

    @Query("""
        SELECT r FROM PayEmp201Return r
        WHERE r.tenantId = :tenantId AND r.payClientId = :clientId AND r.taxMonth >= :from AND r.taxMonth <= :to
        ORDER BY r.taxMonth ASC
    """)
    List<PayEmp201Return> findInRange(@Param("tenantId") UUID tenantId, @Param("clientId") UUID clientId,
                                      @Param("from") LocalDate from, @Param("to") LocalDate to);
}

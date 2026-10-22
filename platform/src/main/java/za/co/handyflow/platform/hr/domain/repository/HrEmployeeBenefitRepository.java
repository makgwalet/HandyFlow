package za.co.handyflow.platform.hr.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.hr.domain.model.HrEmployeeBenefit;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HrEmployeeBenefitRepository extends JpaRepository<HrEmployeeBenefit, UUID> {

    @Query("SELECT b FROM HrEmployeeBenefit b WHERE b.tenantId = :#{#tenantId.value} AND b.employeeId = :employeeId ORDER BY b.effectiveFrom DESC, b.createdAt DESC")
    List<HrEmployeeBenefit> forEmployee(TenantId tenantId, UUID employeeId);

    @Query("SELECT b FROM HrEmployeeBenefit b WHERE b.tenantId = :#{#tenantId.value} AND b.id = :id")
    Optional<HrEmployeeBenefit> findByTenantAndId(TenantId tenantId, UUID id);

    /** Every line that is in force on the date, for the whole company, so a pay run reads them in one go. */
    @Query("""
        SELECT b FROM HrEmployeeBenefit b WHERE b.tenantId = :#{#tenantId.value}
        AND b.effectiveFrom <= :on AND (b.effectiveTo IS NULL OR b.effectiveTo >= :on)""")
    List<HrEmployeeBenefit> activeOn(TenantId tenantId, LocalDate on);
}

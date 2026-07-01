// security/domain/repository/GradeRateRepository.java
package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.GradeRate;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradeRateRepository extends JpaRepository<GradeRate, UUID> {

    /**
     * Most recent rate for a grade effective on or before a given date.
     * This is the rate that applies for a shift on that date.
     */
    @Query("""
        SELECT r FROM GradeRate r
        WHERE r.tenantId = :tenantId
        AND r.grade = :grade
        AND r.effectiveFrom <= :asOf
        ORDER BY r.effectiveFrom DESC
        LIMIT 1
        """)
    Optional<GradeRate> findEffectiveRate(TenantId tenantId, String grade, LocalDate asOf);

    @Query("SELECT r FROM GradeRate r WHERE r.tenantId = :tenantId ORDER BY r.grade, r.effectiveFrom DESC")
    List<GradeRate> findAllByTenant(TenantId tenantId);
}

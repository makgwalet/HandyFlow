// security/domain/repository/GuardScreeningRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.GuardScreeningRecord;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GuardScreeningRepository extends JpaRepository<GuardScreeningRecord, UUID> {

    @Query("""
        SELECT r FROM GuardScreeningRecord r
        WHERE r.tenantId = :tenantId
        AND r.guardId = :guardId
        ORDER BY r.createdAt DESC
        """)
    List<GuardScreeningRecord> findByGuard(TenantId tenantId, UUID guardId);

    @Query("""
        SELECT COUNT(r) > 0 FROM GuardScreeningRecord r
        WHERE r.guardId = :guardId
        AND r.result = 'FAIL'
        """)
    boolean hasFailedScreening(UUID guardId);

    @Query("""
        SELECT COUNT(r) > 0 FROM GuardScreeningRecord r
        WHERE r.guardId = :guardId
        AND r.result = 'PENDING'
        """)
    boolean hasPendingScreening(UUID guardId);

    /** Records due for renewal within the warning window. */
    @Query("""
        SELECT r FROM GuardScreeningRecord r
        WHERE r.tenantId = :tenantId
        AND r.result = 'PASS'
        AND r.nextDueAt IS NOT NULL
        AND r.nextDueAt <= :warnDate
        ORDER BY r.nextDueAt
        """)
    List<GuardScreeningRecord> findDueSoon(TenantId tenantId, LocalDate warnDate);
}

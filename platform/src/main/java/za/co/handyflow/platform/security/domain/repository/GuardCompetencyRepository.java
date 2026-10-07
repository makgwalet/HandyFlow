// security/domain/repository/GuardCompetencyRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.GuardCompetency;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuardCompetencyRepository extends JpaRepository<GuardCompetency, UUID> {

    @Query("""
        SELECT c FROM GuardCompetency c
        WHERE c.tenantId = :tenantId AND c.guardId = :guardId AND c.deletedAt IS NULL
        ORDER BY c.type, c.createdAt DESC
        """)
    List<GuardCompetency> findActiveForGuard(TenantId tenantId, UUID guardId);

    @Query("""
        SELECT c FROM GuardCompetency c
        WHERE c.tenantId = :tenantId AND c.guardId = :guardId AND c.id = :id AND c.deletedAt IS NULL
        """)
    Optional<GuardCompetency> findActiveForGuardById(TenantId tenantId, UUID guardId, UUID id);

    /** Competencies that expire on or before the cut-off (not yet expired before `from`), across the tenant's guards. */
    @Query("""
        SELECT c FROM GuardCompetency c
        WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL AND c.expiryDate IS NOT NULL
          AND c.expiryDate >= :from AND c.expiryDate <= :to
        ORDER BY c.expiryDate
        """)
    List<GuardCompetency> findExpiringBetween(TenantId tenantId, java.time.LocalDate from, java.time.LocalDate to);
}

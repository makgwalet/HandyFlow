// security/domain/repository/GuardLocationPingRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.GuardLocationPing;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface GuardLocationPingRepository extends JpaRepository<GuardLocationPing, UUID> {

    /**
     * Full ping history for a guard within a time range — not used by the
     * live map (which reads security_guard_current_location instead), but
     * available now for the future patrol-pattern-analysis work the audit
     * doc flagged as a "New technology opportunity."
     */
    @Query("""
        SELECT p FROM GuardLocationPing p
        WHERE p.tenantId = :tenantId
        AND p.guardId = :guardId
        AND p.recordedAt >= :from
        AND p.recordedAt < :to
        ORDER BY p.recordedAt
        """)
    List<GuardLocationPing> findByGuardInRange(TenantId tenantId, UUID guardId, Instant from, Instant to);
}
// security/domain/repository/PatrolRoundRepository.java

package za.co.handyflow.platform.security.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.handyflow.platform.security.domain.model.PatrolRound;
import za.co.handyflow.platform.shared.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatrolRoundRepository extends JpaRepository<PatrolRound, UUID> {

    @Query("""
        SELECT r FROM PatrolRound r
        WHERE r.shiftId = :shiftId
        ORDER BY r.roundNumber
        """)
    List<PatrolRound> findByShift(UUID shiftId);

    /**
     * The "current" round for a shift — the lowest-numbered round that is
     * not yet COMPLETE. Used by PatrolRoundService.routeScanToRound() to
     * attribute an incoming scan to the right round.
     */
    @Query("""
        SELECT r FROM PatrolRound r
        WHERE r.shiftId = :shiftId
        AND r.status IN ('EXPECTED', 'IN_PROGRESS')
        ORDER BY r.roundNumber
        LIMIT 1
        """)
    Optional<PatrolRound> findCurrentRound(UUID shiftId);

    /**
     * Rounds whose expected window has passed but are still EXPECTED
     * (no scans at all) — picked up by the missed-round scheduler.
     */
    @Query("""
        SELECT r FROM PatrolRound r
        WHERE r.tenantId = :tenantId
        AND r.status = 'EXPECTED'
        AND r.expectedEndAt < CURRENT_TIMESTAMP
        """)
    List<PatrolRound> findOverdueExpected(TenantId tenantId);
}
